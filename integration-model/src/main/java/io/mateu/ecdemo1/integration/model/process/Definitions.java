package io.mateu.ecdemo1.integration.model.process;

/** The ids of the integration's process definitions, as they are in ec-definitions. */
public final class Definitions {

    public static final String PROJECT_RESERVATION = "proyectar-reserva";
    public static final String PROJECT_CANCELLATION = "proyectar-cancelacion";
    public static final String PROJECT_PARTNER = "proyectar-interlocutor";

    /** The message a waiting process is resumed with once its last cause is resolved. */
    public static final String CAUSES_RESOLVED_MESSAGE = "causes-resolved";

    private Definitions() {
    }
}
