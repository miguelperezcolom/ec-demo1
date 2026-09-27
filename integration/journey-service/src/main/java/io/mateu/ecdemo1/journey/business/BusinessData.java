package io.mateu.ecdemo1.journey.business;

import java.util.List;

/**
 * What the spans do not say, as the systems that own it say it now: the booking in the CRS, its
 * people in the MDM and Salesforce, its Opera profiles and its stay in the front office, the causes
 * it waited on and the equivalences its codes have. Read when the journey is shown; a system that
 * does not answer leaves its part empty and is named in {@code unavailable}.
 */
public record BusinessData(Booking booking, List<Passenger> passengers, List<String> operaProfiles, Stay stay,
                           List<CauseInfo> causes, List<String> translations, List<String> unavailable) {

    public static BusinessData empty() {
        return new BusinessData(null, List.of(), List.of(), null, List.of(), List.of(), List.of());
    }

    /** The booking in the CRS, now. */
    public record Booking(String hotelCode, String status, long version, String channel, String holder,
                          String arrival, String departure, String pmsReservationId, String cancellation) {
    }

    public record Passenger(int passenger, String role, String customerId, String name, String status,
                            String customerRoute, String salesforceContactId, String salesforceContactUrl) {

        public String who() {
            return "HOLDER".equals(role) || passenger == 0 ? "Titular" : "Pasajero " + (passenger + 1);
        }
    }

    public record Stay(String status, String url) {

        public String statusText() {
            if (status == null) {
                return "Estancia";
            }
            return switch (status) {
                case "ARRIVING" -> "Por llegar";
                case "IN_HOUSE" -> "En casa";
                case "DEPARTED" -> "Salida";
                case "CANCELLED" -> "Cancelada";
                case "NO_SHOW" -> "No show";
                default -> status;
            };
        }
    }

    /** A cause this booking waited on — a missing equivalence, a refusal of Opera — and how it ended. */
    public record CauseInfo(String key, String type, String description, String status, String openedAt,
                            String resolvedAt, String resolvedBy) {

        public boolean open() {
            return "OPEN".equals(status);
        }
    }
}
