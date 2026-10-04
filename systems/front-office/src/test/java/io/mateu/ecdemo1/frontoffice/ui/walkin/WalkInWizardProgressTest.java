package io.mateu.ecdemo1.frontoffice.ui.walkin;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.ui.checkin.CheckInWizard;
import io.mateu.uidl.annotations.WizardProgress;
import io.mateu.uidl.annotations.WizardProgressStyle;
import org.junit.jupiter.api.Test;

/**
 * The desk's wizards show their steps across the top: in Redwood a train over the step. RAIL would
 * turn the walk-in into Redwood's Guided Process, with its steps in a column on the right.
 */
class WalkInWizardProgressTest {

  @Test
  void theWalkInShowsItsStepsAcrossTheTopLikeTheCheckIn() {
    assertThat(WalkInWizard.class.getAnnotation(WizardProgress.class).value())
        .isEqualTo(WizardProgressStyle.STEPS)
        .isEqualTo(CheckInWizard.class.getAnnotation(WizardProgress.class).value());
  }
}
