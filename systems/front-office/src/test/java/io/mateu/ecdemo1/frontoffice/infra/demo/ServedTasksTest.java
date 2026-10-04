package io.mateu.ecdemo1.frontoffice.infra.demo;

import io.mateu.ecdemo1.contracts.testing.ServedTasks;
import org.junit.jupiter.api.Test;

/**
 * The tasks the front office serves (EngineTasks, its own worker: no TaskRegistration here), published
 * to contracts/workers/front-office.tasks — what deploy/demo/check-contracts.sh matches against every
 * task ec-definitions references.
 */
class ServedTasksTest {

  @Test
  void theServedTasksAreTheOnesItsWorkerServes() {
    ServedTasks.publish("front-office", EngineTasks.SERVED);
  }
}
