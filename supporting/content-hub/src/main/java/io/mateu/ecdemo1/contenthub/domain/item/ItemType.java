package io.mateu.ecdemo1.contenthub.domain.item;

public enum ItemType {
    ARTICLE("Artículo"), PAGE("Página"), NOTICE("Aviso"), LINK("Enlace"), DOCUMENT("Documento");

    private final String label;

    ItemType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
