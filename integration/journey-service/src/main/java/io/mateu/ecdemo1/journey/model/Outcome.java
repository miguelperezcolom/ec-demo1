package io.mateu.ecdemo1.journey.model;

/** Where the journey stands. */
public enum Outcome {
    DONE("Completado"),
    WAITING("Esperando"),
    IN_PROGRESS("En curso"),
    FAILED("Con error"),
    NOT_PROJECTED("Sin proyectar");

    final String label;

    Outcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
