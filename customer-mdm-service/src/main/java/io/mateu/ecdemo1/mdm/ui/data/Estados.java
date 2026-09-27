package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** How the business reads what the MDM and the systems around it say, in words and badges. */
final class Estados {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    private Estados() {
    }

    static Status customer(CustomerStatus status) {
        return switch (status == null ? CustomerStatus.PROVISIONAL : status) {
            case CONSOLIDATED -> new Status(StatusType.SUCCESS, "Consolidado");
            case MERGED -> new Status(StatusType.NONE, "Fusionado");
            case PROVISIONAL -> new Status(StatusType.WARNING, "Provisional");
        };
    }

    static Status changeRequest(String status) {
        if (ChangeRequest.Status.APPROVED.name().equals(status)) {
            return new Status(StatusType.SUCCESS, "Aprobada");
        }
        if (ChangeRequest.Status.REJECTED.name().equals(status)) {
            return new Status(StatusType.DANGER, "Rechazada");
        }
        return new Status(StatusType.WARNING, "Pendiente");
    }

    /** A CRS booking's status: Pending, Confirmed, Cancelled. */
    static String booking(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "Confirmed" -> "Confirmada";
            case "Cancelled" -> "Cancelada";
            case "Pending" -> "Pendiente";
            default -> status;
        };
    }

    /** A front office stay's status. */
    static String stay(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "ARRIVING" -> "Por llegar";
            case "IN_HOUSE" -> "En casa";
            case "DEPARTED" -> "Salió";
            case "CANCELLED" -> "Cancelada";
            case "NO_SHOW" -> "No show";
            default -> status;
        };
    }

    static String day(LocalDate date) {
        return date == null ? "" : DAY.format(date);
    }

    static String moment(Instant instant) {
        return instant == null ? "" : MOMENT.format(instant);
    }
}
