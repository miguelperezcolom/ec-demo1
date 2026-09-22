package io.mateu.ecdemo1.integration.model.process;

/**
 * The names of the process variables the integration's definitions and workers share. A process
 * carries references, never the reservation: whoever needs the data reads it again (HLA, "Datos
 * personales y retención").
 */
public final class ProcessVariables {

    public static final String HOTEL_CODE = "hotelCode";
    public static final String LOCATOR = "locator";
    public static final String PARTNER_CODE = "partnerCode";
    public static final String VERSION = "version";
    public static final String EVENT_ID = "eventId";
    /** The process's own correlation key: what the wait for its causes is correlated by. */
    public static final String PROCESS_KEY = "processKey";
    public static final String DEFINITION_ID = "definitionId";
    /**
     * Who started the process, when it was not a change in the CRS: {@code backfill:<run>} for a
     * reservation a backfill projects. A backfill runs before the integration is active, so its
     * processes do not wait for the activation.
     */
    public static final String ORIGIN = "origin";
    public static final String INTEGRATION_ID = "integrationId";

    public static final String PREPARE_OUTCOME = "prepareOutcome";
    public static final String PROFILE_OUTCOME = "profileOutcome";
    public static final String WRITE_OUTCOME = "writeOutcome";
    public static final String GUEST_PROFILE_ID = "guestProfileId";
    public static final String PMS_RESERVATION_ID = "pmsReservationId";
    public static final String PMS_PROFILE_IDS = "pmsProfileIds";
    public static final String CAUSES = "causes";

    private ProcessVariables() {
    }
}
