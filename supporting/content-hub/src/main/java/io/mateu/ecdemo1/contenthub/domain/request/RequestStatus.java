package io.mateu.ecdemo1.contenthub.domain.request;

/** Where a request is: with the manager, with HR, or decided. */
public enum RequestStatus {
    PENDIENTE_RESPONSABLE("Pendiente de tu responsable"),
    PENDIENTE_RRHH("Pendiente de RRHH"),
    APROBADA("Aprobada"),
    RECHAZADA("Rechazada");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean decided() {
        return this == APROBADA || this == RECHAZADA;
    }
}
