package io.mateu.ecdemo1.frontoffice.ui.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.infra.audit.AuditHistory;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** «Historial» in the desk's words: what, when, who, where, and whether it was done. */
class ReservationHistoryTest {

  @Test
  void theTrailsActionsInTheDesksWordsAndARefusalAsWhatWasRefused() {
    assertThat(ReservationHistory.label("Room change")).isEqualTo("Cambio de habitación");
    assertThat(ReservationHistory.label("Booking cancelled")).isEqualTo("Reserva cancelada en el CRS");
    assertThat(ReservationHistory.label("Check-in refused: unread notices")).isEqualTo("Check-in rechazado");
    assertThat(ReservationHistory.label("Something new")).isEqualTo("Something new");
  }

  @Test
  void eachEntrySaysWhenWhoWhereAndHowItWent() {
    var item = ReservationHistory.item(new AuditHistory.Entry(Instant.parse("2026-09-30T08:15:00Z"), "booking",
        "Booking cancelled", "luis", false, "Motivo desconocido"));

    assertThat(item.title()).isEqualTo("Reserva cancelada en el CRS");
    assertThat(item.description()).isEqualTo("30 sept 10:15 · luis · CRS — Motivo desconocido");
    assertThat(item.status()).isEqualTo("No hecho");
    assertThat(item.statusColor()).isEqualTo("danger");
  }
}
