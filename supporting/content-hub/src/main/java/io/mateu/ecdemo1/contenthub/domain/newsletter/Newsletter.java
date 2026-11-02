package io.mateu.ecdemo1.contenthub.domain.newsletter;

import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What makes a site a newsletter (its id is the site's): the ISSUES. The site gives it its identity,
 * audience, languages and section tree; this adds the LAYOUT a new issue starts with (blocks), where each of
 * its sections takes automatic content from (its own items, and the intranet sections mapped to it), the
 * cadence — when one is due a draft of the next issue appears — and whether that draft is sent as it is
 * (by default, it waits for an editor).
 *
 * <p>It numbers its issues (nº 1, 2, …) and remembers the last cadence slot it opened one for, so that a
 * slot never opens two.
 */
public class Newsletter extends AggregateRoot {

    /** The prefix of a NEWSLETTER_SECTION block's {@code sectionId}: {@code nl:<section of the site>}. */
    public static final String SECTION_PREFIX = "nl:";
    /** The id of the layout's intro block. */
    public static final String INTRO_BLOCK = "intro";

    /**
     * What an editor sets.
     *
     * @param sources per section of the site, the intranet sections whose items it also takes
     */
    public record Settings(List<Block> layout, Map<String, List<String>> sources, Cadence cadence, boolean autoSend) {
        public Settings {
            layout = layout == null ? List.of() : layout.stream().filter(java.util.Objects::nonNull).toList();
            var copy = new LinkedHashMap<String, List<String>>();
            if (sources != null) {
                sources.forEach((k, v) -> copy.put(k, v == null ? List.of() : List.copyOf(v)));
            }
            sources = java.util.Collections.unmodifiableMap(copy);
            cadence = cadence == null ? Cadence.NONE : cadence;
            for (var b : layout) {
                if (!Block.isNarrowing(b.audience())) {
                    throw new IllegalArgumentException("La audiencia de un bloque solo puede estrechar la de la newsletter");
                }
            }
        }

        /** The layout a newsletter starts with: an intro, then a block per section. */
        public static List<Block> defaultLayout(List<io.mateu.ecdemo1.contenthub.domain.navigation.Section> sections) {
            var blocks = new ArrayList<Block>();
            blocks.add(new Block(INTRO_BLOCK, BlockType.HTML, null, "", "<p>Lo más destacado desde el último número.</p>",
                    null, null, null, null, null));
            for (var s : sections) {
                if (s.parentId() == null) {
                    blocks.add(sectionBlock("s-" + s.id(), s.id(), ""));
                }
            }
            return blocks;
        }
    }

    /** A block that holds the items of one of the site's sections. */
    public static Block sectionBlock(String id, String sectionId, String title) {
        return new Block(id, BlockType.NEWSLETTER_SECTION, null, title, null, null, SECTION_PREFIX + sectionId, null,
                null, null);
    }

    /** The site section a NEWSLETTER_SECTION block names ({@code nl:id}, or the id alone). */
    public static String sectionOf(Block b) {
        var s = b.sectionId();
        return s == null ? null : s.startsWith(SECTION_PREFIX) ? s.substring(SECTION_PREFIX.length()) : s;
    }

    private String id;
    private Settings settings;
    private int issued;
    private Instant lastSlot;
    private Instant createdAt;

    @SuppressWarnings("unused") // for the store
    private Newsletter() {
    }

    /** @param siteId the site this newsletter is */
    public static Newsletter create(String siteId, Settings settings, Instant now) {
        var n = new Newsletter();
        n.id = siteId;
        n.createdAt = now;
        n.settings = settings;
        // the slot that has already passed is not this newsletter's: its first issue is the next one
        n.lastSlot = settings.cadence().slotAtOrBefore(now).orElse(null);
        return n;
    }

    /** New settings. A new cadence starts from now: the slot that has just passed opens nothing. */
    public void configure(Settings settings, Instant now) {
        var before = this.settings;
        this.settings = settings;
        if (!settings.cadence().equals(before.cadence())) {
            restart(now);
        }
    }

    /** Back after being off: the slots that passed meanwhile open nothing. */
    public void restart(Instant now) {
        var current = settings.cadence().slotAtOrBefore(now).orElse(null);
        if (lastSlot == null || current != null && current.isAfter(lastSlot)) {
            lastSlot = current;
        }
    }

    /** The slot an issue is due for now: the latest one passed, if no issue was opened for it yet. */
    public Optional<Instant> dueSlot(Instant now) {
        return settings.cadence().slotAtOrBefore(now).filter(s -> lastSlot == null || s.isAfter(lastSlot));
    }

    /**
     * Opens the next issue: the next number, the layout's blocks (with their own ids) and the items chosen
     * for each section. For a slot of the cadence the slot is spent: it never opens another.
     *
     * @param name the site's name, for the subject
     */
    public NewsletterIssue openIssue(String name, Instant slot, Map<String, List<String>> items, Instant now) {
        if (slot != null) {
            if (lastSlot != null && !slot.isAfter(lastSlot)) {
                throw new IllegalStateException("Ese momento de la periodicidad ya tiene su número");
            }
            lastSlot = slot;
        }
        issued++;
        var issueId = slot != null ? id + "-" + Cadence.key(slot) : id + "-n" + issued;
        var date = (slot != null ? slot : now).atZone(Cadence.ZONE).toLocalDate();
        var n = issued;
        var blocks = settings.layout().stream().map(b -> b.withId(b.id() + "-" + n)).toList();
        return NewsletterIssue.draft(issueId, id, issued, date, slot, name + " · nº " + issued, blocks,
                items == null ? Map.of() : items, now);
    }

    /** The next number, for an issue brought in from elsewhere (the migration). */
    public int nextNumber() {
        return ++issued;
    }

    /** Spends a slot (the migration: a week that already had its edition). */
    public void spend(Instant slot) {
        if (slot != null && (lastSlot == null || slot.isAfter(lastSlot))) {
            lastSlot = slot;
        }
    }

    /**
     * The site section an item goes to in an issue: for one of the site's own items, its section; for one
     * of the intranet's, the first site section whose sources take its section or one of its ancestors.
     *
     * @param siteSections the site's sections, in order
     */
    public Optional<String> sectionFor(boolean ownItem, String sectionId, Collection<String> ancestors,
                                       List<String> siteSections) {
        if (sectionId == null) {
            return Optional.empty();
        }
        if (ownItem) {
            return siteSections.contains(sectionId) ? Optional.of(sectionId) : Optional.empty();
        }
        for (var s : siteSections) {
            var sources = settings.sources().getOrDefault(s, List.of());
            if (sources.contains(sectionId) || ancestors != null && ancestors.stream().anyMatch(sources::contains)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public String id() {
        return id;
    }

    public Settings settings() {
        return settings;
    }

    public int issued() {
        return issued;
    }

    public Instant lastSlot() {
        return lastSlot;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
