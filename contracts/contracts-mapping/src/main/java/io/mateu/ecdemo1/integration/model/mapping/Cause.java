package io.mateu.ecdemo1.integration.model.mapping;

/**
 * A reason for processes to wait, identified so that every process waiting for the same thing
 * waits on the same cause — N processes blocked by one missing code are one incident, not N.
 *
 * @param key {@code type:scope:subject}, e.g. {@code MISSING_MAPPING:PMI01:BOARD:MP} — no slashes,
 *            so that it travels whole as a single segment of a route or a URL
 */
public record Cause(String key, CauseType type, String description) {

    /** Joins the parts of a key. */
    public static final String SEPARATOR = ":";

    static String key(CauseType type, Object... parts) {
        var key = new StringBuilder(type.name());
        for (var part : parts) {
            key.append(SEPARATOR).append(part);
        }
        return key.toString();
    }

    public static Cause missingMapping(String hotelCode, CodeType codeType, String code) {
        return new Cause(key(CauseType.MISSING_MAPPING, hotelCode, codeType, code), CauseType.MISSING_MAPPING,
                "%s %s of hotel %s has no approved equivalent in the PMS".formatted(codeType, code, hotelCode));
    }

    public static Cause missingPartner(String partnerCode) {
        return new Cause(key(CauseType.MISSING_PARTNER, partnerCode), CauseType.MISSING_PARTNER,
                "Partner %s is not a profile in the PMS yet".formatted(partnerCode));
    }

    public static Cause notYetProjected(String hotelCode, String locator) {
        return new Cause(key(CauseType.NOT_YET_PROJECTED, hotelCode, locator), CauseType.NOT_YET_PROJECTED,
                "Reservation %s of hotel %s has not reached the PMS yet".formatted(locator, hotelCode));
    }

    public static Cause pmsRejectedPartner(String partnerCode, String reason) {
        return new Cause(key(CauseType.PMS_REJECTED, "partner", partnerCode), CauseType.PMS_REJECTED,
                "The PMS refused partner %s: %s".formatted(partnerCode, reason));
    }

    /**
     * Opera refused a step of a reservation. Writing the reservation into Opera, with Opera's error code
     * in the reason, the cause is that refusal at that hotel — {@code
     * PMS_REJECTED:MRU01:upsert-reservation:RSV00138}, no rooms of the type left — and every reservation
     * it refuses the same way waits on it: one incident with N reservations behind it, not N. The
     * desk's and the folio's steps (a check-in, a charge) stay the reservation's own: each is fixed on
     * that stay — another room, another amount — and resolving one must not relaunch the others.
     */
    public static Cause pmsRejectedReservation(String hotelCode, String locator, String step, String reason) {
        var code = SHARED_REFUSAL_STEPS.contains(step) ? pmsErrorCode(reason) : null;
        if (code != null) {
            return new Cause(key(CauseType.PMS_REJECTED, hotelCode, step, code), CauseType.PMS_REJECTED,
                    "The PMS refused %s of reservations of hotel %s: %s".formatted(step, hotelCode, reason));
        }
        return new Cause(key(CauseType.PMS_REJECTED, hotelCode, locator, step), CauseType.PMS_REJECTED,
                "The PMS refused %s of reservation %s of hotel %s: %s".formatted(step, locator, hotelCode, reason));
    }

    /** The key a reservation's own refusal has: the one a refusal with no error code waits on. */
    public static String pmsRejectedReservationKey(String hotelCode, String locator, String step) {
        return key(CauseType.PMS_REJECTED, hotelCode, locator, step);
    }

    /** What the keys of the shared refusals of a step at a hotel start with — one per Opera error code. */
    public static String pmsRejectedStepPrefix(String hotelCode, String step) {
        return key(CauseType.PMS_REJECTED, hotelCode, step) + SEPARATOR;
    }

    /** The steps whose refusals with the same Opera code share one cause. */
    public static final java.util.Set<String> SHARED_REFUSAL_STEPS = java.util.Set.of("upsert-reservation");

    static final java.util.regex.Pattern PMS_ERROR_CODE = java.util.regex.Pattern.compile("\\b([A-Z]{2,6}\\d{3,6})\\b");

    /** Opera's error code in a refusal's reason («… on Room Type level — RSV00138»): the last one; null if none. */
    static String pmsErrorCode(String reason) {
        if (reason == null) {
            return null;
        }
        String code = null;
        for (var m = PMS_ERROR_CODE.matcher(reason); m.find(); ) {
            code = m.group(1);
        }
        return code;
    }

    public static Cause integrationInactive(String hotelCode) {
        return new Cause(key(CauseType.INTEGRATION_INACTIVE, hotelCode), CauseType.INTEGRATION_INACTIVE,
                "The integration of hotel %s is not active".formatted(hotelCode));
    }
}
