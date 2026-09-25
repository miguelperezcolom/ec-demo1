package io.mateu.ecdemo1.integrations.config;

import io.mateu.ecdemo1.integrations.worker.TaskHandlers;
import io.mateu.workflow.ddd.DomainEvent;
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
 * The engine's tasks for the integrations, handled on the consumer thread: a reply the broker will not
 * take leaves the offset uncommitted and the task is redelivered — every step is idempotent.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StreamFunctions {

    final TaskHandlers tasks;
    final StreamBridge streamBridge;

    /**
     * The engine's tasks for the integrations. A step that throws is answered as an error, which the
     * engine retries with backoff per the step's definition; an unknown step is left for whoever
     * owns it.
     */
    @Bean
    public Consumer<DomainEvent> consumeTasks() {
        return event -> {
            if (!(event instanceof TaskExecutionRequested task)) {
                return;
            }
            var handler = tasks.handler(task).orElse(null);
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
