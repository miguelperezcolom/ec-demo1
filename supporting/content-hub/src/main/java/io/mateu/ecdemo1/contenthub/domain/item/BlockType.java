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
    MEDIA("Medio"),
    /** "Hola, <name>": the reader's name. */
    GREETING("Saludo"),
    /** What the reader must still confirm having read (nothing, if nothing). */
    PENDING("Pendientes de confirmar"),
    /** The latest notices the reader can see; how many in {@code limit}. */
    NOTICES("Últimos avisos"),
    /** The latest news and pages the reader can see, of a section or of all; how many in {@code limit}. */
    LATEST("Últimas noticias"),
    /**
     * A screen or element of a registered external UI (a UI source): a Mateu UI federated, a web component
     * or, as a last resort, an iframe. In an e-mail, a link to see it on the web.
     */
    REMOTE("Componente remoto");

    private final String label;

    BlockType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
