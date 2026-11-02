package io.mateu.ecdemo1.contenthub.domain.uisource;

/** How an external UI is drawn inside the intranet. */
public enum UiSourceKind {
    /**
     * A Mateu UI behind the gateway ({@code /_loyalty}, {@code /_notices}…), federated as the shells do it:
     * the renderer the shell loaded asks it for a route and draws its UIDL. The preferred kind.
     */
    MATEU_REMOTE("UI de Mateu (federada)"),
    /** A custom element: a script behind the gateway that defines it, and its tag. */
    WEB_COMPONENT("Web component"),
    /** A page of an allow-listed https origin in a sandboxed iframe. The last resort: no token, no theme. */
    IFRAME("Iframe (último recurso)");

    private final String label;

    UiSourceKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
