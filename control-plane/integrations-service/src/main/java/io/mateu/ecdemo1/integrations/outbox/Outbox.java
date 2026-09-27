package io.mateu.ecdemo1.integrations.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.command.MappingCommand;
import io.mateu.ecdemo1.integration.model.command.ProjectReservation;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationResolved;
import io.mateu.ecdemo1.integrations.clients.PartnerCommand;
import io.mateu.ecdemo1.messaging.engine.EngineOutbox;
import io.mateu.workflow.ddd.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * What must leave only if the decision that produced it was saved: the start of an onboarding and
 * the messages that open its gates, the notifications, and the commands to other services — to the
 * mapping, to the master of partners, to the CRS adapter. A decision rolled back asks nothing of
 * anyone; a decision saved has its commands on the way, and the other side takes each once (its
 * inbox deduplicates on the command's id).
 *
 * <p>This service's messages, in its words; the outbox itself — the table, the relay, the trace
 * context carried to the record — is the shared one ({@link io.mateu.ecdemo1.messaging.Outbox}).
 */
@Component
@RequiredArgsConstructor
public class Outbox {

    public static final String ENGINE = "outboxUpstream";
    public static final String NOTIFICATIONS = "notifications";
    public static final String AUDIT = "audit";
    public static final String RESOLUTIONS = "resolutions";
    public static final String MAPPING_COMMANDS = "mappingCommands";
    public static final String PARTNER_COMMANDS = "partnerCommands";
    public static final String PROJECTIONS = "projectionRequests";
    public static final String FRONT_OFFICE_COMMANDS = "frontOfficeCommands";

    final io.mateu.ecdemo1.messaging.Outbox outbox;
    final EngineOutbox engine;
    final ObjectMapper objectMapper;
    final Clock clock;

    /**
     * A request to the engine. A process started here joins the trace it was asked for in: the
     * current context goes in the request's {@code traceContext}, which the engine keeps with the
     * process and hands on to every task it dispatches for it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendToEngine(DomainEvent event) {
        engine.append(event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendNotification(NotificationRequested notification) {
        write(NOTIFICATIONS, notification.dedupKey(), "NotificationRequested",
                serialise(NotificationRequested.class, notification));
    }

    /** What the notifications about this subject asked for is no longer waiting: they close in every inbox. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendResolution(String subject, String resolvedBy) {
        write(RESOLUTIONS, subject, "NotificationResolved",
                serialise(NotificationResolved.class, new NotificationResolved(subject, resolvedBy, clock.instant())));
    }

    /** A person's auditable action, for the audit service (HLA F016). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendAudit(AuditedAction action) {
        write(AUDIT, action.actionId(), "AuditedAction", serialise(AuditedAction.class, action));
    }

    /** A command for the mapping. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendToMapping(MappingCommand command) {
        write(MAPPING_COMMANDS, command.key(), command.getClass().getSimpleName(), serialise(MappingCommand.class, command));
    }

    /** A command for the master of partners. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendToPartners(PartnerCommand command) {
        write(PARTNER_COMMANDS, command.key(), command.getClass().getSimpleName(), serialise(PartnerCommand.class, command));
    }

    /** A reservation for the CRS adapter to project: the backfill's. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendProjection(ProjectReservation request) {
        write(PROJECTIONS, request.key(), "ProjectReservation", serialise(ProjectReservation.class, request));
    }

    /** A command for the hotel's front office: the PMS's catalogue, from the pms-fo integration. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendToFrontOffice(io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand command) {
        write(FRONT_OFFICE_COMMANDS, command.key(), command.getClass().getSimpleName(),
                serialise(io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.class, command));
    }

    private String serialise(Class<?> as, Object value) {
        try {
            return objectMapper.writerFor(as).writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + value, e);
        }
    }

    private void write(String binding, String key, String type, String payload) {
        outbox.append(binding, key, type, payload, null);
    }
}
