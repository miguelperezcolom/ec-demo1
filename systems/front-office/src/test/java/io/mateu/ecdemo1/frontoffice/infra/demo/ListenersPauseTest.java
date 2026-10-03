package io.mateu.ecdemo1.frontoffice.infra.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.demoreset.ConsumerPause;
import io.mateu.ecdemo1.frontoffice.infra.mdm.CustomerEvents;
import io.mateu.ecdemo1.frontoffice.infra.notices.NoticeEvents;
import io.mateu.ecdemo1.frontoffice.infra.pms.FrontOfficeCommands;
import io.mateu.ecdemo1.frontoffice.infra.registration.RegistrationRuleEvents;
import org.junit.jupiter.api.Test;

/** What the demo's reset holds still around itself: the four listeners — never the worker that runs it. */
class ListenersPauseTest {

  @Test
  void theFourListenersArePausedTheWorkerIsNot() {
    for (var listener : new Class<?>[] {FrontOfficeCommands.class, CustomerEvents.class, NoticeEvents.class,
        RegistrationRuleEvents.class}) {
      assertThat(ConsumerPause.class.isAssignableFrom(listener)).as(listener.getSimpleName()).isTrue();
    }
    assertThat(ConsumerPause.class.isAssignableFrom(EngineWorker.class)).isFalse();
  }
}
