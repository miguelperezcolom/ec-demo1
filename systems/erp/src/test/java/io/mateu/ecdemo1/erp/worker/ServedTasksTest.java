package io.mateu.ecdemo1.erp.worker;

import io.mateu.ecdemo1.contracts.testing.ServedTasks;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The tasks this service serves, as its registrations say, published to contracts/workers/erp.tasks
 * — what deploy/demo/check-contracts.sh matches against every task ec-definitions references.
 */
class ServedTasksTest {

    @Test
    void theServedTasksAreTheOnesItsRegistrationsDeclare() {
        ServedTasks.publish("erp", ServedTasks.of(Mockito::mock, new ErpTasks()));
    }
}
