package io.mateu.ecdemo1.mapping.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.command.MappingCommand;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.mapping.commands.MappingCommands;
import io.mateu.ecdemo1.mapping.config.StreamFunctions;
import io.mateu.ecdemo1.mapping.config.TolerantReader;
import io.mateu.ecdemo1.mapping.outbox.Outbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * What the mapping publishes — notifications, their resolutions, audited actions — as its outbox
 * really writes them, checked against each topic's schema; and every example of mapping-commands
 * taken by its consumer.
 */
class MappingContractsTest {

    static final Instant AT = Instant.parse("2026-11-12T09:30:00Z");

    static ObjectMapper bootMapper() {
        try (var context = new AnnotationConfigApplicationContext(JacksonAutoConfiguration.class)) {
            return context.getBean(ObjectMapper.class);
        }
    }

    final ObjectMapper boot = bootMapper();
    final io.mateu.ecdemo1.messaging.Outbox shared = mock(io.mateu.ecdemo1.messaging.Outbox.class);
    final Outbox outbox = new Outbox(shared, null, boot, Clock.fixed(AT, ZoneOffset.UTC));

    List<String> written(String binding) {
        var payloads = ArgumentCaptor.forClass(String.class);
        verify(shared).append(eq(binding), anyString(), anyString(), payloads.capture(), any());
        return payloads.getAllValues();
    }

    @Test
    void notificationsResolutionsAndAuditAreWhatTheirSchemasSay() {
        outbox.appendNotification(new NotificationRequested("N-1", NotificationType.PROPOSAL_READY, "MRU01",
                "proposals:MRU01", "Proposals ready", "The agent proposed 4 equivalences", "/mapping/proposals",
                "proposals:MRU01", AT));
        outbox.appendResolution("proposals:MRU01", "ana");
        outbox.appendAudit(new AuditedAction("A-1", AT, "mapping-service", "approve-proposal", "MRU01", "ana",
                "{\"proposal\":\"P-3\"}", true, "approved"));

        written(Outbox.NOTIFICATIONS).forEach(Contracts.topic("notifications")::assertValid);
        written(Outbox.RESOLUTIONS).forEach(Contracts.topic("notification-resolutions")::assertValid);
        written(Outbox.AUDIT).forEach(Contracts.topic("audit")::assertValid);
    }

    @Test
    void everyExampleOfMappingCommandsIsTakenByTheConsumer() {
        var commands = mock(MappingCommands.class);
        var consumer = new StreamFunctions(commands, new TolerantReader(boot)).consumeMappingCommands();

        var examples = Contracts.topic("mapping-commands").examples();
        examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        var read = ArgumentCaptor.forClass(MappingCommand.class);
        verify(commands, times(examples.size())).handle(read.capture());
        assertThat(read.getAllValues()).allSatisfy(c -> assertThat(c.commandId()).isNotBlank())
                .hasAtLeastOneElementOfType(MappingCommand.DefineEquivalence.class)
                .hasAtLeastOneElementOfType(MappingCommand.RequestAgentProposal.class)
                .hasAtLeastOneElementOfType(MappingCommand.ResolveCauseIfOpen.class)
                .hasAtLeastOneElementOfType(MappingCommand.RecordPartnerProfile.class);
    }
}
