package io.mateu.ecdemo1.contenthub.domain.item;

public enum ItemType {
    ARTICLE("Artículo"), PAGE("Página"), NOTICE("Aviso"), LINK("Enlace"), DOCUMENT("Documento"),
    /** A page whose content is a screen of a registered external UI: its URL is {@code ui:<source><route>}. */
    REMOTE_PAGE("Pantalla externa");

    private final String label;

    ItemType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
