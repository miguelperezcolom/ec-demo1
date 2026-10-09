package io.mateu.ecdemo1.loyalty.infra;

import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.loyalty.PostgresAndBroker;
import io.mateu.ecdemo1.loyalty.application.Loyalty;
import io.mateu.ecdemo1.loyalty.infra.in.async.CustomerEventsConsumer;
import io.mateu.ecdemo1.loyalty.infra.in.async.FrontOfficeEventsConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The topics the loyalty service reads, as their owners publish them: every example of
 * front-office-events and customers is read by the real consumer beans (with the application's mapper)
 * and taken without error — the stay closed earns its points, the merge moves the membership.
 */
class LoyaltyContractsTest extends PostgresAndBroker {

    @Autowired
    FrontOfficeEventsConsumer frontOffice;
    @Autowired
    CustomerEventsConsumer customers;
    @Autowired
    Loyalty loyalty;

    @Test
    void everyExampleOfFrontOfficeEventsIsReadAndAClosedStayEarnsPoints() {
        // The customer the examples' closed stay is held by.
        loyalty.upsert("RC42", new Loyalty.MemberUpdate("C-00042", null, 0L, null));

        var examples = Contracts.topic("front-office-events").examples();
        assertThat(examples).isNotEmpty();
        examples.forEach(json -> {
            assertThat(frontOffice.read(json.getBytes())).as(json).isNotNull();
            frontOffice.consumeFrontOfficeEvents().accept(MessageBuilder.withPayload(json.getBytes()).build());
        });

        assertThat(loyalty.get("RC42").points).isPositive();
        assertThat(loyalty.accrualsOf("RC42")).isNotEmpty();
    }

    @Test
    void everyExampleOfCustomersIsReadAndAMergeMovesTheMembership() {
        // The customer the examples' merge absorbs.
        loyalty.upsert("RC51", new Loyalty.MemberUpdate("C-00051", null, 0L, null));

        var examples = Contracts.topic("customers").examples();
        assertThat(examples).isNotEmpty();
        examples.forEach(json -> {
            assertThat(customers.read(json.getBytes())).as(json).isNotNull();
            customers.consumeCustomerEvents().accept(MessageBuilder.withPayload(json.getBytes()).build());
        });

        assertThat(loyalty.get("RC51").customerCode).isEqualTo("C-00042");
    }
}
