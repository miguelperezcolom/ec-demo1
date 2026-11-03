package io.mateu.ecdemo1.contenthub.domain.newsletter;

import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * An issue (número) of a newsletter: its number, date and subject, laid out like a page of the intranet —
 * a list of BLOCKS (the intranet's own blocks, each able to narrow the audience, plus the newsletter's
 * section blocks) — and, per section of the newsletter, the items it carries. The web version and the
 * email are two renderings of these same blocks.
 *
 * <p>DRAFT → SCHEDULED → SENT, or CANCELLED. It is sent once per group of recipients who see the same and
 * read the same language; each group's send is recorded on its own, so a failed group is retried without
 * resending the others. Sent or cancelled, it is not changed.
 */
public class NewsletterIssue extends AggregateRoot {

    public enum Status {
        DRAFT("Borrador"), SCHEDULED("Programado"), SENT("Enviado"), CANCELLED("Cancelado");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One group's send. */
    public record GroupSend(String hash, String language, int recipients, List<String> itemIds, Instant sentAt,
                            String error) {
    }

    /** How long after each failed pass the next one goes. Past the last, it stops until someone says otherwise. */
    static final List<Duration> RETRIES = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1), Duration.ofHours(6));

    private String id;
    private String newsletterId;
    private int number;
    private LocalDate date;
    private Instant slot;
    private String subject;
    private List<Block> blocks;
    private Map<String, List<String>> sections;
    private Status status;
    private Instant scheduledAt;
    private Instant sentAt;
    private Map<String, GroupSend> sends;
    /** Passes over this issue that left a group unsent, since it was scheduled or last told to retry. */
    private int failedPasses;
    private Instant retryAt;
    private Instant createdAt;
    private Instant updatedAt;

    @SuppressWarnings("unused") // for the store
    private NewsletterIssue() {
    }

    static NewsletterIssue draft(String id, String newsletterId, int number, LocalDate date, Instant slot, String subject,
                                 List<Block> blocks, Map<String, List<String>> sections, Instant now) {
        var i = new NewsletterIssue();
        i.id = id;
        i.newsletterId = newsletterId;
        i.number = number;
        i.slot = slot;
        i.status = Status.DRAFT;
        i.sends = new LinkedHashMap<>();
        i.createdAt = now;
        i.edit(subject, date, blocks, sections, now);
        return i;
    }

    /** An issue as it was before newsletters had issues (the migration), with its status and sends. */
    public static NewsletterIssue imported(String id, String newsletterId, int number, LocalDate date, Instant slot,
                                           String subject, List<Block> blocks, Map<String, List<String>> sections,
                                           Status status, Instant scheduledAt, Instant sentAt, List<GroupSend> sends,
                                           Instant now) {
        var i = draft(id, newsletterId, number, date, slot, subject, blocks, sections, now);
        i.status = status;
        i.scheduledAt = scheduledAt;
        i.sentAt = sentAt;
        sends.forEach(s -> i.sends.put(groupKey(s.hash(), s.language()), s));
        return i;
    }

    public void edit(String subject, LocalDate date, List<Block> blocks, Map<String, List<String>> sections, Instant now) {
        requireEditable();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("El número necesita un asunto");
        }
        for (var b : blocks == null ? List.<Block>of() : blocks.stream().filter(java.util.Objects::nonNull).toList()) {
            if (!Block.isNarrowing(b.audience())) {
                throw new IllegalArgumentException("La audiencia del bloque «" + b.id()
                        + "» solo puede estrechar la de la newsletter (departamentos, hoteles, puestos)");
            }
        }
        this.subject = subject.trim();
        this.date = date == null ? (this.date == null ? LocalDate.ofInstant(now, Cadence.ZONE) : this.date) : date;
        this.blocks = new ArrayList<>(blocks == null ? List.of() : blocks.stream().filter(java.util.Objects::nonNull).toList());
        var copy = new LinkedHashMap<String, List<String>>();
        if (sections != null) {
            sections.forEach((k, v) -> copy.put(k, List.copyOf(new LinkedHashSet<>(v == null ? List.of() : v))));
        }
        this.sections = copy;
        this.updatedAt = now;
    }

    /** Replaces the blocks only (the visual editor). */
    public void editBlocks(List<Block> blocks, Instant now) {
        edit(subject, date, blocks, sections, now);
    }

    /** Adds items to a section, after the ones it has (what was published since the issue was opened). */
    public int addItems(Map<String, List<String>> more, Instant now) {
        requireEditable();
        var added = 0;
        for (var e : more.entrySet()) {
            var current = new LinkedHashSet<>(sections.getOrDefault(e.getKey(), List.of()));
            var allIds = itemIds();
            for (var id : e.getValue()) {
                if (!allIds.contains(id) && current.add(id)) {
                    added++;
                }
            }
            sections.put(e.getKey(), List.copyOf(current));
        }
        updatedAt = now;
        return added;
    }

    public void schedule(Instant at) {
        requireEditable();
        if (itemIds().isEmpty() && blocks.stream().noneMatch(b -> b.type() == BlockType.HTML || b.type() == BlockType.BANNER)) {
            throw new IllegalStateException("Un número vacío no se programa");
        }
        scheduledAt = at;
        status = Status.SCHEDULED;
        failedPasses = 0;
        retryAt = null;
    }

    public void unschedule() {
        requireEditable();
        if (anyGroupSent()) {
            throw new IllegalStateException("Ya ha salido para algunos grupos: no vuelve a borrador");
        }
        status = Status.DRAFT;
        scheduledAt = null;
    }

    public void cancel() {
        requireEditable();
        if (anyGroupSent()) {
            throw new IllegalStateException("Ya ha salido para algunos grupos: no se cancela");
        }
        status = Status.CANCELLED;
        scheduledAt = null;
    }

    public boolean due(Instant now) {
        return status == Status.SCHEDULED && scheduledAt != null && !scheduledAt.isAfter(now)
                && !sendingStopped() && (retryAt == null || !retryAt.isAfter(now));
    }

    /** A pass left a group unsent: the next one waits, longer each time, and after the last it stops. */
    public void sendFailed(Instant now) {
        failedPasses++;
        retryAt = sendingStopped() ? null : now.plus(RETRIES.get(failedPasses - 1));
    }

    /** It gave up on what failed; the groups already sent stay sent. */
    public boolean sendingStopped() {
        return status == Status.SCHEDULED && failedPasses > RETRIES.size();
    }

    /** Someone has fixed what failed: the groups still unsent go on the next pass. */
    public void retrySending() {
        if (!sendingStopped()) {
            throw new IllegalStateException("Este número no ha dejado de intentar su envío");
        }
        failedPasses = 0;
        retryAt = null;
    }

    public static String groupKey(String hash, String language) {
        return hash + "|" + language;
    }

    public boolean groupSent(String hash, String language) {
        var s = sends.get(groupKey(hash, language));
        return s != null && s.sentAt() != null && s.error() == null;
    }

    boolean anyGroupSent() {
        return sends.values().stream().anyMatch(s -> s.sentAt() != null && s.error() == null);
    }

    public void recordGroup(GroupSend send) {
        if (status != Status.SCHEDULED) {
            throw new IllegalStateException("Solo sale un número programado");
        }
        sends.put(groupKey(send.hash(), send.language()), send);
    }

    /** Every group went: it is sent, and no longer changes. */
    public void markSent(Instant now) {
        if (sends.values().stream().anyMatch(s -> s.error() != null)) {
            throw new IllegalStateException("Hay grupos con error");
        }
        status = Status.SENT;
        sentAt = now;
    }

    public boolean editable() {
        return status == Status.DRAFT || status == Status.SCHEDULED;
    }

    private void requireEditable() {
        if (!editable()) {
            throw new IllegalStateException("Un número " + status.label().toLowerCase() + " no se modifica");
        }
    }

    /** Every item it carries, section after section, once. */
    public List<String> itemIds() {
        var out = new LinkedHashSet<String>();
        sections.values().forEach(out::addAll);
        return List.copyOf(out);
    }

    public String id() {
        return id;
    }

    public String newsletterId() {
        return newsletterId;
    }

    public int number() {
        return number;
    }

    public LocalDate date() {
        return date;
    }

    public Instant slot() {
        return slot;
    }

    public String subject() {
        return subject;
    }

    public List<Block> blocks() {
        return blocks.stream().filter(java.util.Objects::nonNull).toList();
    }

    public Map<String, List<String>> sections() {
        return java.util.Collections.unmodifiableMap(sections);
    }

    public List<String> section(String key) {
        return sections.getOrDefault(key, List.of());
    }

    public Status status() {
        return status;
    }

    public Instant scheduledAt() {
        return scheduledAt;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<GroupSend> sends() {
        return List.copyOf(sends.values());
    }
}
