package io.mateu.ecdemo1.frontoffice.ui.reservas;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange.FieldChange;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange.KardexStatus;
import io.mateu.uidl.data.StatusItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The holder's row in the guests rail carries the state of what the desk changed. */
class KardexOnThePaxTest {

  static final Guest PABLO = Guest.fromReservation("C-1", "Pablo Martín", null, "pablo@example.com", "+34 600000001");
  static final StatusItem ROW = StatusItem.builder().id("1").title("Pablo Martín").description("Adulto")
      .status("Kárdex OK").statusColor("success").actionId2("rellenarPax").build();

  static KardexChange change(KardexStatus status, String reason) {
    var pending = KardexChange.pending("C-1", List.of(new FieldChange("teléfono", "+34 600000001", "+34 611222333")),
        Instant.now()).sent("CR-1");
    return status == KardexStatus.PENDING ? pending : pending.decided(status, reason, Instant.now());
  }

  @Test
  void pendingMarksTheGuestAndTheField() {
    var row = ReservaOverview.conKardex(ROW, change(KardexStatus.PENDING, null), PABLO);

    assertThat(row.status()).isEqualTo("Pendiente de Salesforce");
    assertThat(row.statusColor()).isEqualTo("warning");
    assertThat(row.lines()).containsExactly("Teléfono: +34 611222333 — pendiente de Salesforce");
    assertThat(row.actionId2()).isEqualTo("rellenarPax");
  }

  @Test
  void rejectedSaysWhatStaysAndWhy() {
    var row = ReservaOverview.conKardex(ROW, change(KardexStatus.REJECTED, "Número equivocado"), PABLO);

    assertThat(row.status()).isEqualTo("Rechazado en Salesforce");
    assertThat(row.statusColor()).isEqualTo("error");
    assertThat(row.lines()).containsExactly(
        "Teléfono: +34 611222333 rechazado — se queda +34 600000001", "Motivo: Número equivocado");
  }

  @Test
  void aRejectedFieldTheMasterHoldsAsProposedIsNotMarked() {
    var rejected = KardexChange.pending("C-1", java.util.List.of(
            new FieldChange("teléfono", "+34 600000001", "+34 611222333"),
            new FieldChange("email", "pablo@example.com", "pablo@gmail.example.com")), Instant.now())
        .sent("CR-1").decided(KardexStatus.REJECTED, null, Instant.now());
    var master = Guest.fromReservation("C-1", "Pablo Martín", null, "pablo@example.com", "+34 611222333");

    assertThat(rejected.lines(master)).containsExactly("Email: pablo@gmail.example.com rechazado — se queda pablo@example.com");
  }

  @Test
  void approvedLeavesNoMark() {
    var approved = change(KardexStatus.APPROVED, "ignored");

    assertThat(approved.marked()).isFalse();
    assertThat(approved.reason()).isNull();
  }

  @Test
  void theKardexFormListsEachChangedFieldWithItsState() {
    var rejected = change(KardexStatus.REJECTED, "Número equivocado");

    assertThat(ReservaOverview.camposEnSalesforce(rejected, PABLO)).singleElement().satisfies(item -> {
      assertThat(item.title()).isEqualTo("Teléfono");
      assertThat(item.description()).isEqualTo("Propuesto +34 611222333 — se queda +34 600000001");
      assertThat(item.status()).isEqualTo("Rechazado");
      assertThat(item.statusColor()).isEqualTo("error");
      assertThat(item.lines()).containsExactly("Motivo: Número equivocado");
    });
    assertThat(ReservaOverview.camposEnSalesforce(change(KardexStatus.PENDING, null), PABLO)).singleElement()
        .satisfies(item -> assertThat(item.status()).isEqualTo("Pendiente de Salesforce"));
  }

  @Test
  void aChangeKeptBeforeTheFieldsReadsAsOneLine() {
    var old = new KardexChange("C-1", "CR-0", KardexStatus.PENDING, "email a → b", null, null, Instant.now(), null, true);

    assertThat(old.lines(PABLO)).containsExactly("Pendiente de Salesforce · email a → b");
  }
}
