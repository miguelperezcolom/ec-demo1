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

    public static Cause pmsRejectedReservation(String hotelCode, String locator, String step, String reason) {
        return new Cause(key(CauseType.PMS_REJECTED, hotelCode, locator, step), CauseType.PMS_REJECTED,
                "The PMS refused %s of reservation %s of hotel %s: %s".formatted(step, locator, hotelCode, reason));
    }

    public static Cause integrationInactive(String hotelCode) {
        return new Cause(key(CauseType.INTEGRATION_INACTIVE, hotelCode), CauseType.INTEGRATION_INACTIVE,
                "The integration of hotel %s is not active".formatted(hotelCode));
    }
}
