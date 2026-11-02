package io.mateu.ecdemo1.contenthub.domain.item;

/** What happened to an item, as its history tells it. */
public enum HistoryAction {
    CREATED("Creado"),
    EDITED("Editado"),
    SENT_TO_REVIEW("Enviado a revisión"),
    PUBLISHED("Publicado"),
    REJECTED("Rechazado"),
    RETIRED("Retirado"),
    RESTORED("Restaurado");

    private final String label;

    HistoryAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
