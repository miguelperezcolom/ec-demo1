package io.mateu.ecdemo1.pmsintegration.config;

import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsTransientException;
import io.mateu.ecdemo1.pmsintegration.worker.RetryWatch;
import io.mateu.ecdemo1.pmsintegration.worker.TaskHandlers;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import io.mateu.workflow.worker.WorkerReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Consumer;

/**
 * The engine's tasks for the PMS adapter, and the MDM's customer events. Handled on the consumer
 * thread. A step that fails is
 * answered as an error and the engine retries it with backoff; a transient failure also goes on the
 * watch that raises the alarm when it lasts.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StreamFunctions {

    final TaskHandlers tasks;
    final RetryWatch retryWatch;
    final StreamBridge streamBridge;
    final io.mateu.ecdemo1.pmsintegration.frontoffice.FrontOfficeWriter frontOffice;
    final TolerantReader reader;

    /**
     * What the MDM says about a customer, taken to the front office's kardex. Opera's guest profile is
     * written by projecting the customer's reservations again, which crs-integration starts from the
     * same event.
     */
    @Bean
    public Consumer<org.springframework.messaging.Message<byte[]>> consumeCustomerEvents() {
        return message -> {
            io.mateu.ecdemo1.integration.model.customer.CustomerEvent event;
            try {
                event = reader.mapper().readValue(message.getPayload(), io.mateu.ecdemo1.integration.model.customer.CustomerEvent.class);
            } catch (java.io.IOException e) {
                log.error("Unreadable customer event, skipped: {}", new String(message.getPayload()), e);
                return;
            }
            frontOffice.kardex(event);
        };
    }

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
                var variables = handler.apply(task);
                retryWatch.succeeded(task);
                WorkerReply.completed(streamBridge, task, variables);
            } catch (WorkerReply.ReplyNotAcceptedException e) {
                throw e;
            } catch (PmsTransientException e) {
                log.warn("Step {} of {}: Opera not available, the engine will retry: {}", task.stepId(), task.processId(), e.getMessage());
                retryWatch.failed(task, value(task, ProcessVariables.HOTEL_CODE), subject(task), e.getMessage());
                WorkerReply.failed(streamBridge, task, List.of(), e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Step {} of {} failed: {}", task.stepId(), task.processId(), e.toString());
                retryWatch.failed(task, value(task, ProcessVariables.HOTEL_CODE), subject(task), e.toString());
                WorkerReply.failed(streamBridge, task, List.of(), e.toString());
            }
        };
    }

    static String subject(TaskExecutionRequested task) {
        var locator = value(task, ProcessVariables.LOCATOR);
        return locator != null ? locator : value(task, ProcessVariables.PARTNER_CODE);
    }

    static String value(TaskExecutionRequested task, String name) {
        return task.variables().stream().filter(v -> name.equals(v.name())).map(Variable::value).findFirst().orElse(null);
    }
}
