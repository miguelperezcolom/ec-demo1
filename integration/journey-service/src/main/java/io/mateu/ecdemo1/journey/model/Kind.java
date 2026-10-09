package io.mateu.ecdemo1.journey.model;

/** What the change that started the journey was. */
public enum Kind {
    CREATED("Creada"),
    MODIFIED("Modificada"),
    CANCELLED("Cancelada"),
    NO_SHOW("No-show"),
    CHECK_IN("Check-in"),
    CHECK_OUT("Check-out"),
    CHARGE("Cargo en recepción"),
    CHARGE_VOID("Cargo anulado en recepción"),
    PAYMENT("Cobro en recepción"),
    PAYMENT_REFUND("Cobro devuelto en recepción"),
    WALK_IN("Walk-in"),
    BACKFILL("Reproyectada (backfill)"),
    RELAUNCH("Reanudada tras resolver sus causas"),
    OTHER("Cambio");

    final String label;

    Kind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** As the integration names the event on its routing span ({@code booking.event}). */
    public static Kind ofEvent(String event) {
        if (event == null) {
            return null;
        }
        return switch (event) {
            case "reservation-created" -> CREATED;
            case "reservation-modified" -> MODIFIED;
            case "reservation-cancelled" -> CANCELLED;
            case "no-show" -> NO_SHOW;
            case "check-in" -> CHECK_IN;
            case "check-out" -> CHECK_OUT;
            case "charge" -> CHARGE;
            case "charge-void" -> CHARGE_VOID;
            case "payment" -> PAYMENT;
            case "payment-refund" -> PAYMENT_REFUND;
            case "backfill" -> BACKFILL;
            case "relaunch" -> RELAUNCH;
            default -> null;
        };
    }
}
