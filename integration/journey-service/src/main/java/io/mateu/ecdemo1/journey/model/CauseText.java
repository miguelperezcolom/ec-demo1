package io.mateu.ecdemo1.journey.model;

/**
 * A cause a process waits on, in Spanish, from its key — the mapping describes them in English, and
 * the key says all a person needs: MISSING_MAPPING:MRU01:ROOM_TYPE:DBL, PMS_REJECTED:MRU01:ABC:step …
 */
public final class CauseText {

    private CauseText() {
    }

    public static String of(String key, String description) {
        if (key == null || key.isBlank()) {
            return description;
        }
        var parts = key.split(":");
        return switch (parts[0]) {
            case "MISSING_MAPPING" -> parts.length >= 4
                    ? "falta la equivalencia en Opera de " + JourneyMapper.codeType(parts[2]).toLowerCase(java.util.Locale.ROOT)
                    + " " + parts[3] + ("chain".equals(parts[1]) ? " (cadena)" : " (" + parts[1] + ")")
                    : "falta una equivalencia";
            case "MISSING_PARTNER" -> "el interlocutor " + (parts.length > 1 ? parts[1] : "") + " aún no es un perfil de Opera";
            case "NOT_YET_PROJECTED" -> "la reserva aún no ha llegado a Opera";
            case "INTEGRATION_INACTIVE" -> "la integración del hotel " + (parts.length > 1 ? parts[1] : "") + " no está activa";
            case "PMS_REJECTED" -> "Opera la rechazó" + reason(description);
            default -> description == null ? key : description;
        };
    }

    /** "…: the reason" → ": the reason" (Opera's own words). */
    static String reason(String description) {
        if (description == null) {
            return "";
        }
        var colon = description.indexOf(": ");
        return colon < 0 ? "" : ": " + description.substring(colon + 2);
    }
}
