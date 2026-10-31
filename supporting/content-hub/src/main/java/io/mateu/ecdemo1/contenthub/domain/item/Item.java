package io.mateu.ecdemo1.contenthub.domain.item;

import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;
import io.mateu.ecdemo1.contenthub.domain.shared.ContentEvents;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * A piece of content — article, page, notice, link or document — with its translations, its blocks and
 * its versions: they change and are published together, which is why they are one aggregate.
 *
 * <p>The editor always works on {@link #draft()}. Publishing freezes the draft into a new
 * {@link ItemVersion}; editing a published item touches only the draft, never what readers see, until
 * the next publication.
 *
 * <p>Workflow: {@code DRAFT → IN_REVIEW → PUBLISHED → RETIRED}; a review can send it back to DRAFT.
 * Once an item has versions, its status describes what readers see, and a pending change of the draft
 * goes through review on its own ({@link #draftInReview()}).
 */
public class Item extends AggregateRoot {

    private String id;
    private ItemType type;
    private String author;
    private String defaultLanguage;
    private ItemStatus status;
    private ItemContent draft;
    private boolean draftChanged;
    private boolean draftInReview;
    private List<ItemVersion> versions;
    private int confirmationVersion;
    private Instant updatedAt;

    @SuppressWarnings("unused") // for the store
    private Item() {
    }

    public static Item create(String id, ItemType type, String author, String defaultLanguage, ItemContent content,
                              Instant now) {
        var item = new Item();
        item.id = id;
        item.type = type;
        item.author = author;
        item.defaultLanguage = defaultLanguage == null || defaultLanguage.isBlank() ? "es" : defaultLanguage;
        item.status = ItemStatus.DRAFT;
        item.draft = content;
        item.draftChanged = true;
        item.versions = new ArrayList<>();
        item.updatedAt = now;
        return item;
    }

    public boolean hasVersions() {
        return !versions.isEmpty();
    }

    public void editDraft(ItemContent content, Instant now) {
        draft = content;
        draftChanged = true;
        draftInReview = false;
        if (!hasVersions()) {
            status = ItemStatus.DRAFT;
        }
        updatedAt = now;
    }

    public void submitForReview(Instant now) {
        if (!hasVersions()) {
            if (status != ItemStatus.DRAFT) {
                throw new IllegalStateException("Solo un borrador se envía a revisión");
            }
            status = ItemStatus.IN_REVIEW;
        } else {
            if (!draftChanged) {
                throw new IllegalStateException("No hay cambios que revisar");
            }
            draftInReview = true;
        }
        updatedAt = now;
    }

    /** The review says no: back to the editor. */
    public void reject(Instant now) {
        if (status == ItemStatus.IN_REVIEW) {
            status = ItemStatus.DRAFT;
        }
        draftInReview = false;
        updatedAt = now;
    }

    /** Whether the draft has gone through review and waits for a publisher. */
    public boolean awaitingPublication() {
        return status == ItemStatus.IN_REVIEW || draftInReview;
    }

    /**
     * Freezes the draft into a new version and makes it what readers see (from its publication date).
     *
     * @param skipReview           true only for someone who may publish without approval (a publisher)
     * @param requireReconfirmation for an item that must be confirmed: whether earlier confirmations stop
     *                             counting with this version
     */
    public ItemVersion publish(String by, boolean skipReview, boolean requireReconfirmation, Instant now) {
        if (!skipReview && !awaitingPublication()) {
            throw new IllegalStateException("Hay que enviarlo a revisión antes de publicarlo");
        }
        validate(draft);
        var content = draft.publishAt() == null ? draft.withPublishAt(now) : draft;
        var version = new ItemVersion(versions.size() + 1, content, now, by);
        versions.add(version);
        if (content.confirmationRequired()
                && (confirmationVersion == 0 || requireReconfirmation)) {
            confirmationVersion = version.number();
        }
        if (!content.confirmationRequired()) {
            confirmationVersion = 0;
        }
        status = ItemStatus.PUBLISHED;
        draftChanged = false;
        draftInReview = false;
        updatedAt = now;
        raise(new ContentEvents.ItemPublished(id, version.number()));
        return version;
    }

    public void retire(Instant now) {
        if (status != ItemStatus.PUBLISHED) {
            throw new IllegalStateException("Solo se retira lo publicado");
        }
        status = ItemStatus.RETIRED;
        updatedAt = now;
        raise(new ContentEvents.ItemRetired(id));
    }

    private void validate(ItemContent content) {
        var main = content.translations().get(defaultLanguage);
        if (main == null || main.title().isBlank()) {
            throw new IllegalStateException("Falta el título en el idioma por defecto (" + defaultLanguage + ")");
        }
        if (content.publishAt() != null && content.expireAt() != null
                && !content.expireAt().isAfter(content.publishAt())) {
            throw new IllegalStateException("La caducidad tiene que ser posterior a la publicación");
        }
        if (content.confirmationRequired() && content.confirmationDeadline() != null && content.publishAt() != null
                && content.confirmationDeadline().isBefore(content.publishAt())) {
            throw new IllegalStateException("La fecha límite de confirmación es anterior a la publicación");
        }
        if ((type == ItemType.LINK || type == ItemType.DOCUMENT) && (content.url() == null || content.url().isBlank())) {
            throw new IllegalStateException("Un " + type.label().toLowerCase() + " necesita su URL");
        }
        var ids = new HashSet<String>();
        for (var block : content.blocks()) {
            if (!ids.add(block.id())) {
                throw new IllegalStateException("Dos bloques con el mismo id: " + block.id());
            }
        }
        if (type != ItemType.PAGE && !content.blocks().isEmpty()) {
            throw new IllegalStateException("Solo las páginas tienen bloques");
        }
    }

    /** What readers see, when it is published. Visibility (dates, audience) is not this method's business. */
    public Optional<ItemVersion> publishedVersion() {
        return status == ItemStatus.PUBLISHED && hasVersions() ? Optional.of(versions.getLast()) : Optional.empty();
    }

    public Optional<ItemVersion> latestVersion() {
        return hasVersions() ? Optional.of(versions.getLast()) : Optional.empty();
    }

    /** Whether a confirmation of {@code version} counts for this item now. */
    public boolean confirmationCounts(int version) {
        return confirmationVersion > 0 && version >= confirmationVersion;
    }

    public String id() {
        return id;
    }

    public ItemType type() {
        return type;
    }

    public String author() {
        return author;
    }

    public String defaultLanguage() {
        return defaultLanguage;
    }

    public ItemStatus status() {
        return status;
    }

    public ItemContent draft() {
        return draft;
    }

    public boolean draftChanged() {
        return draftChanged;
    }

    public boolean draftInReview() {
        return draftInReview;
    }

    public List<ItemVersion> versions() {
        return List.copyOf(versions);
    }

    public int confirmationVersion() {
        return confirmationVersion;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
