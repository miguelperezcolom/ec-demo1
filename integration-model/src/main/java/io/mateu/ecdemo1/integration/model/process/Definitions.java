package io.mateu.ecdemo1.integration.model.process;

/** The ids of the integration's process definitions, as they are in ec-definitions. */
public final class Definitions {

    public static final String PROJECT_RESERVATION = "proyectar-reserva";
    public static final String PROJECT_CANCELLATION = "proyectar-cancelacion";
    public static final String PROJECT_PARTNER = "proyectar-interlocutor";
    /** The hotel says a guest did not arrive: the CRS cancels the booking as a no-show, with its fee (HLA F006, #5). */
    public static final String REGISTER_NO_SHOW = "registrar-no-show";

    /** The onboarding of a hotel's integration: gates from registration to activation (F010). */
    public static final String ONBOARD_INTEGRATION = "alta-integracion";

    /** The message a waiting process is resumed with once its last cause is resolved. */
    public static final String CAUSES_RESOLVED_MESSAGE = "causes-resolved";

    /**
     * The gates of the onboarding, each a message of its own so that a message resent for one gate
     * can never open the next. Correlated by the integration's process key.
     */
    public static final String GATE_CONNECTIVITY = "integration-connectivity-ok";
    public static final String GATE_CONFIGURED = "integration-property-configured";
    public static final String GATE_MAPPING = "integration-mapping-approved";
    public static final String GATE_PARTNERS = "integration-partners-synced";
    public static final String GATE_BACKFILL_CLEAR = "integration-backfill-clear";
    public static final String GATE_WINDOW = "integration-window-covered";
    public static final String GATE_ACTIVATION = "integration-activation-requested";

    private Definitions() {
    }
}
