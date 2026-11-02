package io.mateu.ecdemo1.contenthub.domain.preview;

import io.mateu.ecdemo1.contenthub.domain.item.Item;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A link that shows an item's DRAFT to a reviewer who is not an editor — someone of the department the
 * page is for, a manager who has to agree — as readers would see it, before it is published. It still
 * needs a login (anyone of the intranet), shows the draft read-only and records who opened it.
 *
 * <p>It stops working when it expires, when an editor revokes it, or when the item is published: what it
 * showed is then either what readers see, or older than that. That last one needs no event: the link
 * remembers how many versions the item had when it was made ({@link #versionsAtCreation}), and a
 * publication adds one.
 *
 * @see PreviewLinkStatus
 */
public class PreviewLink {

    /** The longest a link may last: a review is days, not months. */
    public static final Duration MAX_VALIDITY = Duration.ofDays(30);
    public static final Duration DEFAULT_VALIDITY = Duration.ofDays(7);
    /** The views kept with the link, the latest; the count goes on. */
    static final int VIEWS_KEPT = 200;

    /** One opening of the link: who, when. */
    public record View(String userId, Instant at) {
    }

    private String id;
    private String itemId;
    private String createdBy;
    private Instant createdAt;
    private Instant expiresAt;
    private String viewAs;
    private String viewAsLabel;
    private int versionsAtCreation;
    private Instant revokedAt;
    private String revokedBy;
    private long viewCount;
    private List<View> views;

    @SuppressWarnings("unused") // for the store
    private PreviewLink() {
    }

    /**
     * @param viewAs      a profile's hash to show the draft as (as «ver como» does), or null: as the reviewer
     * @param viewAsLabel how that profile is shown to whoever opens the link
     */
    public static PreviewLink create(String id, Item item, String createdBy, Duration validity, String viewAs,
                                     String viewAsLabel, Instant now) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Un enlace necesita su id");
        }
        if (item.hasVersions() && !item.draftChanged()) {
            throw new IllegalStateException("No hay borrador que enseñar: el contenido está publicado tal cual. "
                    + "Comparte su enlace de la intranet.");
        }
        var v = validity == null ? DEFAULT_VALIDITY : validity;
        if (v.isNegative() || v.isZero() || v.compareTo(MAX_VALIDITY) > 0) {
            throw new IllegalArgumentException("Un enlace de vista previa dura de 1 a " + MAX_VALIDITY.toDays() + " días");
        }
        var link = new PreviewLink();
        link.id = id;
        link.itemId = item.id();
        link.createdBy = createdBy;
        link.createdAt = now;
        link.expiresAt = now.plus(v);
        link.viewAs = viewAs == null || viewAs.isBlank() ? null : viewAs;
        link.viewAsLabel = link.viewAs == null ? null : viewAsLabel;
        link.versionsAtCreation = item.versions().size();
        link.views = new ArrayList<>();
        return link;
    }

    /** Where the link stands for this item (the one it was made for) now. */
    public PreviewLinkStatus status(Item item, Instant now) {
        if (revokedAt != null) {
            return PreviewLinkStatus.REVOKED;
        }
        if (item == null || !item.id().equals(itemId)) {
            return PreviewLinkStatus.REVOKED;
        }
        if (item.versions().size() > versionsAtCreation) {
            return PreviewLinkStatus.PUBLISHED;
        }
        if (!now.isBefore(expiresAt)) {
            return PreviewLinkStatus.EXPIRED;
        }
        return PreviewLinkStatus.ACTIVE;
    }

    public void revoke(String by, Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            revokedBy = by;
        }
    }

    /** Someone opened it (and could: check {@link #status} first). */
    public void recordView(String userId, Instant now) {
        viewCount++;
        views.add(new View(userId, now));
        if (views.size() > VIEWS_KEPT) {
            views.subList(0, views.size() - VIEWS_KEPT).clear();
        }
    }

    public long uniqueViewers() {
        return views.stream().map(View::userId).distinct().count();
    }

    public String id() {
        return id;
    }

    public String itemId() {
        return itemId;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String viewAs() {
        return viewAs;
    }

    public String viewAsLabel() {
        return viewAsLabel;
    }

    public int versionsAtCreation() {
        return versionsAtCreation;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public String revokedBy() {
        return revokedBy;
    }

    public long viewCount() {
        return viewCount;
    }

    /** The latest views (up to {@value #VIEWS_KEPT}), oldest first. */
    public List<View> views() {
        return List.copyOf(views);
    }
}
