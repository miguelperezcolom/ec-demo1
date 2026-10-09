package io.mateu.ecdemo1.mdm.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.integration.model.command.CustomerCommand;
import io.mateu.ecdemo1.mdm.commands.CustomerCommands;
import io.mateu.ecdemo1.mdm.config.StreamFunctions;
import io.mateu.ecdemo1.mdm.config.TolerantReader;
import io.mateu.ecdemo1.mdm.outbox.CustomerEvents;
import io.mateu.ecdemo1.mdm.outbox.Outbox;
import io.mateu.ecdemo1.mdm.salesforce.AllowanceNotices;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceBudget;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.Source;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The MDM's topics: what it publishes (customers, notifications, notification-resolutions) checked
 * as its outbox really writes it against each topic's schema; what it takes (customer-commands) by
 * feeding every example of the schema through the consumer the binding runs. No Salesforce: the
 * allowance notices run on a budget that is a mock.
 */
class MdmContractsTest {

    static final Instant AT = Instant.parse("2026-11-12T09:30:00Z");

    static ObjectMapper bootMapper() {
        try (var context = new AnnotationConfigApplicationContext(JacksonAutoConfiguration.class)) {
            return context.getBean(ObjectMapper.class);
        }
    }

    final ObjectMapper boot = bootMapper();
    final Clock clock = Clock.fixed(AT, ZoneOffset.UTC);
    final io.mateu.ecdemo1.messaging.Outbox shared = mock(io.mateu.ecdemo1.messaging.Outbox.class);
    final Outbox outbox = new Outbox(shared, boot, clock);

    List<String> written(String binding, int count) {
        var payloads = ArgumentCaptor.forClass(String.class);
        verify(shared, times(count)).append(eq(binding), any(), anyString(), payloads.capture(), any());
        return payloads.getAllValues();
    }

    static Customer customer() {
        var c = new Customer();
        c.id = "C-00042";
        c.version = 3;
        c.firstName = "Ana";
        c.lastName = "García";
        c.email = "ana@example.com";
        c.nationality = "ES";
        c.birthDate = LocalDate.of(1990, 5, 17);
        c.documentType = "PASSPORT";
        c.documentNumber = "X1234567";
        return c;
    }

    @Test
    void theCustomerEventsTheOutboxWritesAreWhatTheSchemaSays() {
        var sources = mock(SourceRepository.class);
        var source = new Source();
        source.hotelCode = "MRU01";
        source.locator = "12E45";
        when(sources.findByCustomerIdOrderByFirstSeenAsc("C-00042")).thenReturn(List.of(source, source));
        // Two documents the MDM knows of her: the golden record carries both, the main one among them.
        var documents = mock(io.mateu.ecdemo1.mdm.store.CustomerDocumentRepository.class);
        when(documents.findByCustomerIdOrderByFirstSeenAtAsc("C-00042")).thenReturn(List.of(
                document("PASSPORT", "X1234567", "ES"), document("DNI", "12345678Z", "ES")));
        var events = new CustomerEvents(outbox, sources, clock, documents);

        events.changed(customer(), true, "CR-7", "APPROVED", null);
        events.changed(customer(), false, null, "REJECTED", "not the same person");
        events.merged(customer(), "C-00051");

        var schema = Contracts.topic("customers");
        var payloads = written(Outbox.CUSTOMERS, 3);
        payloads.forEach(schema::assertValid);
        assertThat(payloads.getFirst()).contains("\"reservations\":[\"MRU01/12E45\"]");
        assertThat(payloads.getFirst()).contains("\"documents\":[{\"type\":\"PASSPORT\",\"number\":\"X1234567\",\"issuingCountry\":\"ES\"}");
    }

    static io.mateu.ecdemo1.mdm.store.CustomerDocument document(String type, String number, String country) {
        var d = new io.mateu.ecdemo1.mdm.store.CustomerDocument();
        d.type = type;
        d.number = number;
        d.issuingCountry = country;
        return d;
    }

    @Test
    void theAllowanceNoticesAreWhatNotificationsAndTheirResolutionsSay() {
        var budget = mock(SalesforceBudget.class);
        when(budget.usage()).thenReturn("15000/15000");
        var notices = new AllowanceNotices(outbox, new TransactionTemplate(new NoTransactions()), budget);
        var pause = new SalesforceBudget.Pause(AT, AT.plusSeconds(3600), "REQUEST_LIMIT_EXCEEDED");

        notices.paused(pause);
        notices.resumed(pause);

        written(Outbox.NOTIFICATIONS, 1).forEach(Contracts.topic("notifications")::assertValid);
        written(Outbox.RESOLUTIONS, 1).forEach(Contracts.topic("notification-resolutions")::assertValid);
    }

    @Test
    void everyExampleOfCustomerCommandsIsTaken() {
        var commands = mock(CustomerCommands.class);
        var consumer = new StreamFunctions(commands, new TolerantReader(boot)).consumeCustomerCommands();

        var examples = Contracts.topic("customer-commands").examples();
        examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        var taken = ArgumentCaptor.forClass(CustomerCommand.class);
        verify(commands, times(examples.size())).handle(taken.capture());
        assertThat(taken.getAllValues()).allSatisfy(c -> assertThat(c.commandId()).isNotBlank());
        assertThat(taken.getAllValues()).hasAtLeastOneElementOfType(CustomerCommand.ProposeChange.class)
                .hasAtLeastOneElementOfType(CustomerCommand.RecordScannedIdentity.class);
        assertThat(taken.getAllValues()).filteredOn(CustomerCommand.RecordScannedIdentity.class::isInstance)
                .allSatisfy(c -> assertThat(((CustomerCommand.RecordScannedIdentity) c).birthDate()).isNotNull());
    }

    static class NoTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
