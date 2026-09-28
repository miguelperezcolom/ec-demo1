package io.mateu.ecdemo1.pmsintegration.worker;

import io.mateu.ecdemo1.contracts.testing.ServedTasks;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The tasks this service serves, as its registrations say, published to contracts/workers/pms-integration-service.tasks
 * — what deploy/demo/check-contracts.sh matches against every task ec-definitions references.
 */
class ServedTasksTest {

    @Test
    void theServedTasksAreTheOnesItsRegistrationsDeclare() {
        ServedTasks.publish("pms-integration-service", ServedTasks.of(Mockito::mock, new PmsTasks()));
    }
}
