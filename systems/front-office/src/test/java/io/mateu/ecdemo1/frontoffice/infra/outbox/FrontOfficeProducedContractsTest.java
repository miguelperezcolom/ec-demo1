package io.mateu.ecdemo1.frontoffice.infra.outbox;

import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.audit.AuditOutbox;
import io.mateu.ecdemo1.frontoffice.infra.crs.NoShows;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.command.CustomerCommand;
import io.mateu.ecdemo1.messaging.Outbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the front office publishes, as its outboxes really write it (Jackson 3, their own mappers),
 * checked against the topics' schemas: no-show-reports, customer-commands and audit.
 */
class FrontOfficeProducedContractsTest {

    final Outbox outbox = mock(Outbox.class);

    String written(String topic) {
        var payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).append(eq(topic), anyString(), anyString(), payload.capture(), any());
        return payload.getValue();
    }

    @Test
    void aNoShowReportIsWhatTheSchemaSays() {
        var walkIns = mock(WalkIns.class);
        when(walkIns.of("12E45")).thenReturn(Optional.empty());
        new NoShows(walkIns, new CommandOutbox(outbox), "MRU01").report("12E45");

        Contracts.topic("no-show-reports").assertValid(written(CommandOutbox.NO_SHOW_REPORTS));
    }

    @Test
    void aProposedChangeIsWhatTheSchemaSays() {
        new CommandOutbox(outbox).append(CommandOutbox.CUSTOMER_COMMANDS, "C-00042", new CustomerCommand.ProposeChange(
                "CR-FO-1", "C-00042", "Ana García", "ana@example.com", "+34600000000", null, "front office MRU01"));

        Contracts.topic("customer-commands").assertValid(written(CommandOutbox.CUSTOMER_COMMANDS));
    }

    /** A birth date is a LocalDate: it must go as the ISO date the MDM reads. */
    @Test
    void aScannedIdentityIsWhatTheSchemaSays() {
        var command = new CustomerCommand.RecordScannedIdentity("SCAN-1", "MRU01", "12E45", "ST-9", 1, null, "Ana",
                "García", "PASSPORT", "X1234567", LocalDate.of(1990, 5, 17), "ES", "front office MRU01 · ST-9 pax 1");
        new CommandOutbox(outbox).append(CommandOutbox.CUSTOMER_COMMANDS, command.key(), command);

        var json = written(CommandOutbox.CUSTOMER_COMMANDS);
        Contracts.topic("customer-commands").assertValid(json);
        org.assertj.core.api.Assertions.assertThat(json).contains("\"birthDate\":\"1990-05-17\"");
    }

    /** An instant goes as the ISO string audit-service reads. */
    @Test
    void anAuditedActionIsWhatTheSchemaSays() {
        new AuditOutbox(outbox).append(new AuditedAction("A-1", Instant.parse("2026-11-12T09:30:00Z"),
                AuditOutbox.SERVICE, "check-in", "MRU01", "ana", "{\"stay\":\"ST-9\"}", true, "checked in"));

        var json = written("audit");
        Contracts.topic("audit").assertValid(json);
        org.assertj.core.api.Assertions.assertThat(json).contains("\"at\":\"2026-11-12T09:30:00Z\"");
    }

    /** The validator this application uses (the MCP's) does refuse what the schema does not describe. */
    @Test
    void aMessageTheSchemaDoesNotDescribeIsRefused() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Contracts.topic("no-show-reports")
                        .assertValid("{\"commandId\":\"NS-1\",\"hotel\":\"MRU01\"}"))
                .isInstanceOf(AssertionError.class);
    }
}
