package io.mateu.ecdemo1.contenthub.domain.item;

public enum BlockType {
    /** One item, chosen by the editor, shown large. */
    FEATURED("Destacado"),
    /** The latest visible items of a section (or of all), filled when the page is read. */
    AUTOMATIC_LISTING("Listado automático"),
    BANNER("Banner"),
    QUICK_LINKS("Enlaces rápidos"),
    HTML("HTML"),
    /** An uploaded file or an external video/document, from the media library. */
    MEDIA("Medio");

    private final String label;

    BlockType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
