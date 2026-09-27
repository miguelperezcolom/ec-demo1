package io.mateu.ecdemo1.frontoffice.domain.guest;

import java.time.Instant;
import java.util.List;

/**
 * The last change the desk made to a guest's data that the chain's master — Salesforce, through the
 * MDM — has to decide. The kardex shows the new data at once, marked as pending; approved, it stays;
 * rejected, the master's data comes back.
 *
 * @param requestId the MDM's change request, once it was sent there
 * @param fields    what changed, field by field: each field shows its own state
 * @param reason    why the master rejected it, when it says
 * @param synced    whether the MDM has it: an edit made while the MDM does not answer is sent later
 */
public record KardexChange(String guestId, String requestId, KardexStatus status, String changes,
                           List<FieldChange> fields, String reason,
                           Instant requestedAt, Instant decidedAt, boolean synced) {

  public enum KardexStatus { PENDING, APPROVED, REJECTED }

  /** One field the desk changed: what it had, and what the desk proposes. */
  public record FieldChange(String field, String before, String after) {

    /** How the kardex names it. */
    public String label() {
      return switch (field) {
        case "nombre" -> "Nombre";
        case "documento" -> "Documento";
        case "email" -> "Email";
        case "teléfono" -> "Teléfono";
        default -> field;
      };
    }
  }

  public KardexChange {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }

  public static KardexChange pending(String guestId, List<FieldChange> fields, Instant now) {
    return new KardexChange(guestId, null, KardexStatus.PENDING, describe(fields), fields, null, now, null, false);
  }

  public KardexChange sent(String requestId) {
    return new KardexChange(guestId, requestId, status, changes, fields, reason, requestedAt, decidedAt, true);
  }

  public KardexChange decided(KardexStatus decision, String reason, Instant now) {
    return new KardexChange(guestId, requestId, decision, changes, fields,
        decision == KardexStatus.REJECTED ? reason : null, requestedAt, now, true);
  }

  public boolean pending() {
    return status == KardexStatus.PENDING;
  }

  /** Whether the kardex marks it on the guest: pending and rejected do; an approved change just stays. */
  public boolean marked() {
    return status != KardexStatus.APPROVED;
  }

  /** How the kardex reads it. */
  public String label() {
    return switch (status) {
      case PENDING -> "Pendiente de Salesforce";
      case APPROVED -> "Aprobado";
      case REJECTED -> "Rechazado en Salesforce";
    };
  }

  /**
   * One line per changed field, as the guest's row shows it. Rejected, it says what was proposed and
   * what stays — the master's — and why, when the master said.
   */
  public List<String> lines(Guest current) {
    if (status == KardexStatus.APPROVED) {
      return List.of();
    }
    if (fields.isEmpty()) {
      // A change kept before the kardex kept its fields.
      return changes == null || changes.isBlank() ? List.of() : List.of(label() + " · " + changes);
    }
    var lines = new java.util.ArrayList<String>();
    for (var f : shownFor(current)) {
      if (status == KardexStatus.PENDING) {
        lines.add(f.label() + ": " + shown(f.after()) + " — pendiente de Salesforce");
      } else {
        lines.add(f.label() + ": " + shown(f.after()) + " rechazado — se queda "
            + shown(current == null ? f.before() : current.valueOf(f.field())));
      }
    }
    if (status == KardexStatus.REJECTED && reason != null && !reason.isBlank()) {
      lines.add("Motivo: " + reason);
    }
    return lines;
  }

  /**
   * The fields the guest's row marks: rejected, one the master holds as the desk proposed it lost
   * nothing and is not marked.
   */
  public List<FieldChange> shownFor(Guest current) {
    if (status != KardexStatus.REJECTED || current == null) {
      return fields;
    }
    return fields.stream().filter(f -> !java.util.Objects.equals(f.after(), current.valueOf(f.field()))).toList();
  }

  public static String describe(List<FieldChange> fields) {
    return String.join(", ", fields.stream()
        .map(f -> f.field() + " " + shown(f.before()) + " → " + shown(f.after())).toList());
  }

  static String shown(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }
}
