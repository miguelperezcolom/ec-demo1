package io.mateu.ecdemo1.crsintegration.config;

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
 * The three things this service consumes. Each on the consumer thread and synchronously: a failure
 * leaves the offset uncommitted and the message is redelivered, which the inboxes make harmless.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StreamFunctions {

    final CrsEventHandler crsEventHandler;
    final ProcessRouter router;
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
