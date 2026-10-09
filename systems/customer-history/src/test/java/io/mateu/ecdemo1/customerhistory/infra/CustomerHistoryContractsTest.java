package io.mateu.ecdemo1.customerhistory.infra;

import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.customerhistory.Containers;
import io.mateu.ecdemo1.customerhistory.application.CustomerHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The topics it reads, as their schemas' examples say their producers write them: every example of
 * front-office-events and of customers goes through the application's real consumer beans — the ones
 * it does not take are skipped without an error, the ones it takes land where they should.
 */
@SpringBootTest
class CustomerHistoryContractsTest extends Containers {

    @Autowired
    @Qualifier("consumeFrontOfficeEvents")
    Consumer<Message<byte[]>> frontOfficeEvents;
    @Autowired
    @Qualifier("consumeCustomerEvents")
    Consumer<Message<byte[]>> customerEvents;
    @Autowired
    CustomerHistory history;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void empty() {
        jdbc.update("truncate customer_stay, customer_alias, inbox_entry");
    }

    @Test
    void everyExampleOfFrontOfficeEventsIsReadAndAStayClosedIsKeptForItsCustomers() {
        var examples = Contracts.topic("front-office-events").examples();
        assertThat(examples).anySatisfy(json -> assertThat(json).contains("\"stay-closed\""));

        examples.forEach(json -> frontOfficeEvents.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        // E-9: C-00042 and pax-2 — only the customer of the chain
        assertThat(jdbc.queryForList("select customer_id from customer_stay", String.class)).containsExactly("C-00042");
        var summary = history.summary("C-00042");
        assertThat(summary.stays()).isEqualTo(1);
        assertThat(summary.lastStays()).singleElement().satisfies(s -> assertThat(s.hotelCode()).isEqualTo("MRU01"));
    }

    @Test
    void everyExampleOfCustomersIsReadAndAMergeBecomesAnAlias() {
        var examples = Contracts.topic("customers").examples();

        examples.forEach(json -> customerEvents.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        assertThat(jdbc.queryForList("select absorbed_id || '>' || survivor_id from customer_alias", String.class))
                .containsExactly("C-00051>C-00042");
        assertThat(history.summary("C-00051").customerId()).isEqualTo("C-00042");
    }

    @Test
    void whatIsNotJsonOrNotKnownIsDroppedWithoutAnError() {
        frontOfficeEvents.accept(MessageBuilder.withPayload("not json".getBytes()).build());
        frontOfficeEvents.accept(MessageBuilder.withPayload("{\"type\":\"something-new\",\"eventId\":\"X\"}".getBytes()).build());
        customerEvents.accept(MessageBuilder.withPayload("[]".getBytes()).build());
        assertThat(jdbc.queryForObject("select count(*) from customer_stay", Integer.class)).isZero();
    }
}
