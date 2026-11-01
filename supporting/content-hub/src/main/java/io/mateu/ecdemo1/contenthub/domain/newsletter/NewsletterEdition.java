package io.mateu.ecdemo1.contenthub.domain.newsletter;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A newsletter edition: the items chosen, in order, for an audience, sent at a time. It is sent once per
 * group of recipients who see the same (profile hash) and read the same language, and each group's send
 * is recorded on its own: a failed group is retried without sending the others again. Once sent, it is
 * not changed.
 */
public class NewsletterEdition extends AggregateRoot {

    public enum Status { DRAFT, SCHEDULED, SENT }

    /** One group's send. */
    public record GroupSend(String hash, String language, int recipients, List<String> itemIds, Instant sentAt,
                            String error) {
    }

    private String id;
    private String title;
    private String intro;
    private List<String> itemIds;
    private Audience audience;
    private Instant scheduledAt;
    private Status status;
    private Map<String, GroupSend> sends;

    @SuppressWarnings("unused") // for the store
    private NewsletterEdition() {
    }

    public static NewsletterEdition compose(String id, String title, String intro, List<String> itemIds,
                                            Audience audience) {
        var e = new NewsletterEdition();
        e.id = id;
        e.status = Status.DRAFT;
        e.sends = new LinkedHashMap<>();
        e.edit(title, intro, itemIds, audience);
        return e;
    }

    public void edit(String title, String intro, List<String> itemIds, Audience audience) {
        requireNotSent();
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("La edición necesita un título");
        }
        this.title = title.trim();
        this.intro = intro == null ? "" : intro;
        this.itemIds = new ArrayList<>(itemIds == null ? List.of() : itemIds);
        this.audience = audience == null ? Audience.EVERYONE : audience;
    }

    public void schedule(Instant at) {
        requireNotSent();
        if (itemIds.isEmpty()) {
            throw new IllegalStateException("Una edición sin items no se programa");
        }
        scheduledAt = at;
        status = Status.SCHEDULED;
    }

    public void unschedule() {
        requireNotSent();
        status = Status.DRAFT;
        scheduledAt = null;
    }

    public boolean due(Instant now) {
        return status == Status.SCHEDULED && scheduledAt != null && !scheduledAt.isAfter(now);
    }

    public static String groupKey(String hash, String language) {
        return hash + "|" + language;
    }

    public boolean groupSent(String hash, String language) {
        var s = sends.get(groupKey(hash, language));
        return s != null && s.sentAt() != null && s.error() == null;
    }

    public void recordGroup(GroupSend send) {
        if (status == Status.SENT) {
            throw new IllegalStateException("La edición ya está enviada");
        }
        sends.put(groupKey(send.hash(), send.language()), send);
    }

    /** Every group went: no more changes. */
    public void markSent() {
        if (sends.values().stream().anyMatch(s -> s.error() != null)) {
            throw new IllegalStateException("Hay grupos con error");
        }
        status = Status.SENT;
    }

    private void requireNotSent() {
        if (status == Status.SENT) {
            throw new IllegalStateException("Una edición enviada no se modifica");
        }
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String intro() {
        return intro;
    }

    public List<String> itemIds() {
        return List.copyOf(itemIds);
    }

    public Audience audience() {
        return audience;
    }

    public Instant scheduledAt() {
        return scheduledAt;
    }

    public Status status() {
        return status;
    }

    public List<GroupSend> sends() {
        return List.copyOf(sends.values());
    }
}
