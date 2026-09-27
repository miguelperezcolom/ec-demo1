package io.mateu.ecdemo1.journey.model;

/**
 * A passenger as the MDM recognised it on this change.
 *
 * @param matchedBy NEW, EMAIL, DOCUMENT, RESERVATION or SOURCE (already known from this booking)
 */
public record Identity(int passenger, String customerId, String matchedBy) {

    /** How it was recognised, in words. */
    public String how() {
        if (matchedBy == null) {
            return "";
        }
        return switch (matchedBy) {
            case "NEW" -> "cliente nuevo";
            case "EMAIL" -> "reconocido por su email";
            case "DOCUMENT" -> "reconocido por su documento";
            case "RESERVATION" -> "el mismo que otro pasajero de la reserva";
            case "SOURCE" -> "ya conocido de esta reserva";
            case "CUSTOMER" -> "el cliente que el hotel ya conocía";
            default -> matchedBy.toLowerCase();
        };
    }

    public String who() {
        return passenger == 0 ? "Titular" : "Pasajero " + (passenger + 1);
    }
}
