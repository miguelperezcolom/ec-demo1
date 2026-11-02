package io.mateu.ecdemo1.contenthub.domain.health;

/** The checks «Salud del contenido» runs, each a list of findings. */
public enum HealthCheck {
    /** Links and references to what does not exist or is not published, and external URLs that fail. */
    BROKEN_LINKS("Enlaces rotos"),
    /** Media nothing uses: no item, block, slot variant or newsletter. */
    ORPHAN_MEDIA("Medios huérfanos"),
    /** Images with no text alternative, in the library or inline in a body. */
    MISSING_ALT("Imágenes sin texto alternativo"),
    /** Published items about to expire, and expired ones still linked from homes, pages or slots. */
    EXPIRY("Caducidad"),
    /** Published items not updated in a long time, and pages nobody has opened lately. */
    STALE("Contenido desactualizado");

    private final String label;

    HealthCheck(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
