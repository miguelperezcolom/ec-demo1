package io.mateu.ecdemo1.crsintegration.config;

import io.mateu.ecdemo1.integration.model.customer.CustomerChanged;
import io.mateu.ecdemo1.integration.model.customer.CustomerEvent;
import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import io.mateu.ecdemo1.crsintegration.in.CrsEventHandler;
import io.mateu.ecdemo1.crsintegration.router.ProcessRouter;
import io.mateu.ecdemo1.crsintegration.worker.TaskHandlers;
import io.mateu.ecdemo1.integration.model.events.IntegrationEvent;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import io.mateu.workflow.worker.WorkerReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * The four things this service consumes. Each on the consumer thread and synchronously: a failure
 * leaves the offset uncommitted and the message is redelivered, which the inboxes make harmless.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StreamFunctions {

    final CrsEventHandler crsEventHandler;
    final ProcessRouter router;
    final io.mateu.ecdemo1.crsintegration.router.Integrations integrations;
    final TaskHandlers tasks;
    final StreamBridge streamBridge;
    final TolerantReader reader;

    /** What happened in the CRS and in the master of partners. */
    @Bean
    public Consumer<Message<byte[]>> consumeCrsEvents() {
        return message -> {
            try {
                crsEventHandler.handle(reader.mapper().readTree(message.getPayload()));
            } catch (IOException e) {
                log.error("Unreadable CRS event, skipped: {}", new String(message.getPayload()), e);
            }
        };
    }

    /**
     * What the MDM says about a customer. When its data changed, or two customers became one, every
     * reservation it is on is projected again — the PMS's guest profile is written from the MDM — for
     * the hotels that have an integration. A decision that changed nothing (a rejection) moves
     * nothing here: the front office learns it from pms-integration.
     */
    @Bean
    public Consumer<Message<byte[]>> consumeCustomerEvents() {
        return message -> {
            CustomerEvent event;
            try {
                event = reader.mapper().readValue(message.getPayload(), CustomerEvent.class);
            } catch (IOException e) {
                log.error("Unreadable customer event, skipped: {}", new String(message.getPayload()), e);
                return;
            }
            var origin = switch (event) {
                case CustomerChanged c -> c.dataChanged() ? "mdm-update-" + c.customerId() + "-v" + c.version() : null;
                case CustomersMerged m -> "mdm-merge-" + m.absorbedId();
            };
            if (origin == null) {
                return;
            }
            var projected = 0;
            for (var reservation : event.reservations()) {
                var parts = reservation.split("/", 2);
                if (parts.length == 2 && integrations.integrated(parts[0])) {
                    router.project(parts[0], parts[1], origin);
                    projected++;
                }
            }
            log.info("{}: {} of {} reservation(s) projected again", origin, projected, event.reservations().size());
        };
    }

    /** The integration's business events, each turned into the process it calls for. */
    @Bean
    public Consumer<Message<byte[]>> routeIntegrationEvents() {
        return message -> {
            try {
                router.route(reader.mapper().readValue(message.getPayload(), IntegrationEvent.class));
            } catch (IOException e) {
                log.error("Unreadable business event, skipped: {}", new String(message.getPayload()), e);
            }
        };
    }

    /**
     * The engine's tasks for this adapter. A step that throws is answered as an error, which the
     * engine retries with backoff per the step's definition; an unknown step is left for whoever
     * owns it.
     */
    @Bean
    public Consumer<DomainEvent> consumeTasks() {
        return event -> {
            if (!(event instanceof TaskExecutionRequested task)) {
                return;
            }
            var handler = tasks.handlers().get(task.stepId());
            if (handler == null) {
                log.debug("No handler for step {}", task.stepId());
                return;
            }
            try {
                WorkerReply.completed(streamBridge, task, handler.apply(task));
            } catch (WorkerReply.ReplyNotAcceptedException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("Step {} of process {} failed: {}", task.stepId(), task.processId(), e.getMessage());
                WorkerReply.failed(streamBridge, task, List.of(), e.getMessage());
            }
        };
    }
}
