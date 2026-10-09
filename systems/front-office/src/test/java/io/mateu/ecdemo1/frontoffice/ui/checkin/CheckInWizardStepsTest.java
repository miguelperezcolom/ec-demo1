package io.mateu.ecdemo1.frontoffice.ui.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.mateu.ecdemo1.frontoffice.application.CheckInService;
import io.mateu.ecdemo1.frontoffice.application.GuestNotices;
import io.mateu.ecdemo1.frontoffice.application.IncompleteCheckIns;
import io.mateu.ecdemo1.frontoffice.application.StayQueries;
import io.mateu.ecdemo1.frontoffice.application.StayView;
import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOps;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The check-in train is the run's: the steps pending when it opened stay on it — done ones as ✓ —
 * instead of vanishing the moment they stop being pending (identity, once its documents are in).
 */
class CheckInWizardStepsTest {

  final StayQueries queries = mock(StayQueries.class);
  final GuestNotices notices = mock(GuestNotices.class);
  final Stay stay = Stay.fromReservation("S-1", "C-1", "Doble", "Desayuno", LocalDate.now(),
      LocalDate.now().plusDays(2), 2, "Directo · WEB", new BigDecimal("100.00"), List.of());
  final Guest guest = Guest.fromReservation("C-1", "Ana", null, "ana@example.com", null);

  CheckInWizard wizard(int pendingPax) {
    when(queries.view("S-1")).thenReturn(new StayView(stay, guest, null));
    when(queries.find("S-1")).thenReturn(Optional.of(stay));
    when(queries.ops("S-1")).thenReturn(CheckInOps.none().withExtras(true));
    when(queries.pendingPax(any())).thenReturn(pendingPax);
    when(notices.forStay(any(), any())).thenReturn(List.of());
    var wizard = new CheckInWizard(queries, mock(CheckInService.class), mock(RoomRepository.class), notices,
        mock(IncompleteCheckIns.class), mock(io.mateu.ecdemo1.frontoffice.application.Recognition.class));
    wizard.stayId = "S-1";
    wizard.populate();
    return wizard;
  }

  @Test
  void identityPendingWhenTheRunOpenedStaysOnTheTrainOnceDone() {
    var wizard = wizard(2);
    assertThat(wizard.stepApplies("identidad")).isTrue();

    // the documents are in: identity is no longer pending, but it is still this run's step (✓)
    when(queries.pendingPax(any())).thenReturn(0);
    assertThat(wizard.stepApplies("identidad")).isTrue();
  }

  @Test
  void aStepNotPendingWhenTheRunOpenedStaysOff() {
    var wizard = wizard(0);
    assertThat(wizard.stepApplies("identidad")).isFalse();
    assertThat(wizard.stepApplies("avisos")).isFalse();
    assertThat(wizard.stepApplies("extras")).isFalse();
    // no room on the stay: Habitación is this run's; Confirmar always is
    assertThat(wizard.stepApplies("habitacion")).isTrue();
    assertThat(wizard.stepApplies("confirmar")).isTrue();
  }

  @Test
  void theFrozenStepsSurviveTheStateRoundTripAsAString() {
    assertThat(CheckInWizard.pasoDeLaRun("identidad,habitacion", "identidad")).isTrue();
    assertThat(CheckInWizard.pasoDeLaRun("habitacion", "identidad")).isFalse();
    assertThat(CheckInWizard.pasoDeLaRun("", "extras")).isFalse();
    assertThat(CheckInWizard.pasoDeLaRun("", "confirmar")).isTrue();
    assertThat(CheckInWizard.pasoDeLaRun("", "result")).isTrue();
  }
}
