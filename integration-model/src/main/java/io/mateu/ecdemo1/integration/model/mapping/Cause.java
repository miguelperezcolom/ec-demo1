package io.mateu.ecdemo1.integration.model.mapping;

/**
 * A reason for processes to wait, identified so that every process waiting for the same thing
 * waits on the same cause — N processes blocked by one missing code are one incident, not N.
 *
 * @param key {@code type/scope/subject}, e.g. {@code MISSING_MAPPING/PMI01/BOARD/MP}
 */
public record Cause(String key, CauseType type, String description) {

    public static Cause missingMapping(String hotelCode, CodeType codeType, String code) {
        return new Cause("MISSING_MAPPING/" + hotelCode + "/" + codeType + "/" + code, CauseType.MISSING_MAPPING,
                "%s %s of hotel %s has no approved equivalent in the PMS".formatted(codeType, code, hotelCode));
    }

    public static Cause missingPartner(String partnerCode) {
        return new Cause("MISSING_PARTNER/" + partnerCode, CauseType.MISSING_PARTNER,
                "Partner %s is not a profile in the PMS yet".formatted(partnerCode));
    }

    public static Cause notYetProjected(String hotelCode, String locator) {
        return new Cause("NOT_YET_PROJECTED/" + hotelCode + "/" + locator, CauseType.NOT_YET_PROJECTED,
                "Reservation %s of hotel %s has not reached the PMS yet".formatted(locator, hotelCode));
    }

    public static Cause pmsRejected(String subject, String reason) {
        return new Cause("PMS_REJECTED/" + subject, CauseType.PMS_REJECTED,
                "The PMS refused %s: %s".formatted(subject, reason));
    }
}
