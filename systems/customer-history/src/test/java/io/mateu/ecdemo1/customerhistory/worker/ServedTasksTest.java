package io.mateu.ecdemo1.customerhistory.worker;

import io.mateu.ecdemo1.contracts.testing.ServedTasks;
import org.junit.jupiter.api.Test;

/**
 * The tasks this service serves, as its registrations say, published to
 * contracts/workers/customer-history.tasks — what deploy/demo/check-contracts.sh matches against every
 * task ec-definitions references.
 */
class ServedTasksTest {

    @Test
    void theServedTasksAreTheOnesItsRegistrationsDeclare() {
        // null for every parameter rather than Mockito::mock: only the registrations' contract and topic
        // are read, never their handlers — and Mockito's inline mocks cannot mock a class on a JDK 25.
        ServedTasks.publish("customer-history", ServedTasks.of(type -> null, new CustomerHistoryTasks()));
    }
}
