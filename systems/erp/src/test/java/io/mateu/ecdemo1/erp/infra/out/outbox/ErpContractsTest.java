package io.mateu.ecdemo1.erp.infra.out.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.contracts.testing.SchemaFiles;
import io.mateu.ecdemo1.contracts.testing.TopicSpec;
import io.mateu.ecdemo1.erp.application.usecases.PartnerCommands;
import io.mateu.ecdemo1.erp.domain.partner.PartnerChanged;
import io.mateu.ecdemo1.erp.domain.partner.PartnerType;
import io.mateu.ecdemo1.erp.infra.in.async.PartnerCommandsConsumer;
import io.mateu.ecdemo1.messaging.Outbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The master of partners' topics. It owns two — partners, the events it publishes, and
 * partner-commands, what it takes — so their schemas are generated here from its records
 * ({@code -Dcontracts.write=true} writes them to contracts/schemas). The events are checked as the
 * outbox really writes them, and every example of partner-commands is read the way the consumer
 * reads the topic.
 */
class ErpContractsTest {

    static final Instant AT = Instant.parse("2026-11-12T09:30:00Z");

    /** The application's mapper, as Spring Boot's auto-configuration builds it. */
    static ObjectMapper bootMapper() {
        try (var context = new AnnotationConfigApplicationContext(JacksonAutoConfiguration.class)) {
            return context.getBean(ObjectMapper.class);
        }
    }

    final ObjectMapper boot = bootMapper();

    List<String> events() {
        var outbox = mock(Outbox.class);
        new OutboxWriter(outbox, boot, "partnerEvents").append(new PartnerChanged("E-1", "NORDTRAVEL", 4, AT));
        var payloads = ArgumentCaptor.forClass(String.class);
        verify(outbox).append(eq("partnerEvents"), anyString(), anyString(), payloads.capture(), any());
        return payloads.getAllValues();
    }

    @Test
    void theSchemaOfPartnersIsWhatItsEventsAre() {
        var spec = TopicSpec.topic("partners")
                .describedAs("A partner was created or changed, or someone asked for it to be synchronised "
                        + "again. Thin: whoever needs the partner reads it.")
                .ownedBy("erp").keyedBy("partnerCode")
                .producedBy("erp").consumedBy("crs-integration-service")
                .variant("type", "partner-changed", PartnerChanged.class);
        events().forEach(spec::example);
        SchemaFiles.publish(spec);
    }

    @Test
    void theEventsTheOutboxWritesAreWhatTheSchemaSays() {
        var schema = Contracts.topic("partners");
        events().forEach(schema::assertValid);
    }

    @Test
    void theSchemaOfPartnerCommandsIsWhatTheMasterTakes() {
        SchemaFiles.publish(TopicSpec.topic("partner-commands")
                .describedAs("What other services ask of the master of partners without waiting: announce a "
                        + "partner again, bring one in as the PMS has it, record which PMS profile it is. Taken "
                        + "once each (inbox).")
                .ownedBy("erp").keyedBy("partnerCode")
                .producedBy("integrations-service", "crs-integration-service").consumedBy("erp")
                .messages(PartnerCommands.Command.class)
                .example(new PartnerCommands.Resync("CMD-1", "NORDTRAVEL"),
                        new PartnerCommands.Import("CMD-2", "SUNTOURS", PartnerType.TourOperator, "Sun Tours",
                                "16120700", "Wholesaler"),
                        new PartnerCommands.RecordPmsProfile("CMD-3", "NORDTRAVEL", "16120699", "Agent")));
    }

    @Test
    void everyExampleOfPartnerCommandsIsTakenByTheConsumer() {
        var commands = mock(PartnerCommands.class);
        var consumer = new PartnerCommandsConsumer(commands, boot).consumePartnerCommands();

        var examples = Contracts.topic("partner-commands").examples();
        examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        var taken = ArgumentCaptor.forClass(PartnerCommands.Command.class);
        verify(commands, times(examples.size())).handle(taken.capture());
        assertThat(taken.getAllValues()).contains(
                new PartnerCommands.Resync("CMD-1", "NORDTRAVEL"),
                new PartnerCommands.Import("CMD-2", "SUNTOURS", PartnerType.TourOperator, "Sun Tours", "16120700",
                        "Wholesaler"),
                new PartnerCommands.RecordPmsProfile("CMD-3", "NORDTRAVEL", "16120699", "Agent"));
    }
}
