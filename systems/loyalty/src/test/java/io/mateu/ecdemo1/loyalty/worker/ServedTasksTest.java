package io.mateu.ecdemo1.loyalty.worker;

import io.mateu.ecdemo1.contracts.testing.ServedTasks;
import org.junit.jupiter.api.Test;

/**
 * The tasks this service serves, as its registrations say, published to contracts/workers/loyalty.tasks
 * — what deploy/demo/check-contracts.sh matches against every task ec-definitions references.
 */
class ServedTasksTest {

    @Test
    void theServedTasksAreTheOnesItsRegistrationsDeclare() {
        // No mocks: building a registration does not touch the reset, and Mockito cannot instrument
        // DemoReset on a JDK 25 (Byte Buddy), so a null stands in for it.
        ServedTasks.publish("loyalty", ServedTasks.of(type -> null, new LoyaltyTasks()));
    }
}
