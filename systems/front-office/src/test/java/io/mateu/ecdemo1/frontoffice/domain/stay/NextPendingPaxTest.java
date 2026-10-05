package io.mateu.ecdemo1.frontoffice.domain.stay;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** After a scan the desk goes on to the next pax still lacking identity — then to the next step. */
class NextPendingPaxTest {

  static final Guest HOLDER = Guest.fromReservation("C-1", "Ana", null, "ana@example.com", null);
  static final Guest HOLDER_OK = HOLDER.verifyIdentity("X1");
  static final CheckInOps NONE = CheckInOps.none();

  static Stay stay(int pax) {
    return Stay.fromReservation("S-1", "C-1", "Doble", "Desayuno", LocalDate.now(), LocalDate.now().plusDays(2),
        pax, "Directo · WEB", new BigDecimal("100.00"), List.of());
  }

  @Test
  void theHolderScannedGoesOnToTheSecondPax() {
    assertThat(CheckInChecklist.nextPendingPax(stay(3), HOLDER_OK, NONE, 1)).isEqualTo(2);
  }

  @Test
  void aCompletedPaxIsSkipped() {
    var stay = stay(3).scanCompanion(2, "D2");
    assertThat(CheckInChecklist.nextPendingPax(stay, HOLDER_OK, NONE, 1)).isEqualTo(3);
  }

  @Test
  void itWrapsRoundToAnEarlierPaxStillPending() {
    var stay = stay(3).scanCompanion(3, "D3");
    // the desk started on pax 3: the holder and pax 2 are still waiting
    assertThat(CheckInChecklist.nextPendingPax(stay, HOLDER, NONE, 3)).isEqualTo(1);
    assertThat(CheckInChecklist.nextPendingPax(stay, HOLDER_OK, NONE, 3)).isEqualTo(2);
  }

  @Test
  void aNoShowIsNotWaitedFor() {
    var ops = new CheckInOps(false, false, false, false, false, Set.of(2));
    assertThat(CheckInChecklist.nextPendingPax(stay(3), HOLDER_OK, ops, 1)).isEqualTo(3);
  }

  @Test
  void noneLeftIsZero() {
    var stay = stay(2).scanCompanion(2, "D2");
    assertThat(CheckInChecklist.nextPendingPax(stay, HOLDER_OK, NONE, 2)).isZero();
    assertThat(CheckInChecklist.nextPendingPax(stay(1), HOLDER_OK, NONE, 1)).isZero();
  }
}
