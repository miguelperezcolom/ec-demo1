package io.mateu.ecdemo1.contenthub.domain.item;

/**
 * Where an item stands. Scheduled and expired are not states: a published item whose publication date
 * is still ahead is scheduled, one whose expiry has passed is expired — both read off the instant, with
 * no job and no transition.
 */
public enum ItemStatus {
    DRAFT("Borrador"), IN_REVIEW("En revisión"), PUBLISHED("Publicado"), RETIRED("Retirado");

    private final String label;

    ItemStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
