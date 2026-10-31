package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The read side every consumer filters through: the published content, each piece with the profile
 * hashes that see it (the projection the visibility policy computed), and the filter
 * {@code hashes ∩ {hash, TODOS} ≠ ∅} plus the instant — applied inside the store, never in memory.
 *
 * <p>A subject is anything with an audience of its own: an item, a page's block, a slot's variant. Each
 * keeps its effective audience next to its hashes, so the projection can be recalculated from here.
 */
public interface ContentIndex {

    enum Kind { ITEM, BLOCK, VARIANT }

    /** A subject of the projection: its effective audience and the hashes that currently see it. */
    record Subject(Kind kind, String key, Audience audience, Set<String> hashes) {
    }

    // ── writing the projection ──────────────────────────────────────────────────────────────────

    /**
     * Replaces the item's documents (one per translation) with its published version, its hashes and its
     * blocks' hashes. Called with the item's latest version, whatever its status.
     */
    void indexItem(Item item, Set<String> hashes, Map<String, Set<String>> blockHashes);

    void indexSlot(Slot slot, Map<String, Set<String>> variantHashes);

    List<Subject> subjects();

    void updateHashes(Kind kind, String key, Set<String> hashes);

    /** Adds one hash to the subjects given (a profile seen for the first time). */
    void addHash(String hash, Collection<Subject> subjects);

    /** Takes a hash out of every subject (a profile nobody has any more). */
    void removeHash(String hash);

    // ── the universe of profiles ───────────────────────────────────────────────────────────────

    Map<String, EmployeeAttributes> profiles();

    boolean knowsProfile(String hash);

    void registerProfile(EmployeeAttributes attributes);

    void forgetProfile(String hash);

    /** The version of the item the projection reflects, and when it was applied. */
    Optional<Applied> applied(String itemId);

    record Applied(int version, Instant at) {
    }

    // ── the consumers' reads: always filtered by hash and instant inside the store ──────────────

    Page<VisibleItem> visibleItems(String hash, String language, Instant now, ContentQuery query);

    boolean isVisible(String itemId, String hash, Instant now);

    /** The sections that directly hold something visible. */
    Set<String> sectionsWithVisibleContent(String hash, Instant now);

    /** The page's blocks visible to the hash, in their order. */
    List<String> visibleBlockIds(String itemId, String hash);

    Optional<VisibleVariant> topVisibleVariant(String slotKey, String hash);

    // ── the RAG's passages ──────────────────────────────────────────────────────────────────────

    /** Passages not embedded yet, oldest items first. */
    List<Passage> passagesToEmbed(int limit);

    void saveEmbedding(Passage passage, float[] embedding);

    /**
     * The passages most similar to a question, among those of items visible to the hash now, in the
     * reader's language (or the item's default) — the visibility filter inside the same query.
     */
    List<Passage> similarVisiblePassages(String hash, String language, Instant now, float[] question, int limit);

    record Passage(String itemId, String language, int chunkNo, String title, String text, double distance) {
    }

    /** Whether some published item that uses the media asset is visible to the hash now. */
    boolean isMediaVisible(String mediaId, String hash, Instant now);

    record Page<T>(List<T> content, long total) {
    }

    record VisibleVariant(String slotKey, String variantId, int priority, String title, String html, String linkUrl) {
    }
}
