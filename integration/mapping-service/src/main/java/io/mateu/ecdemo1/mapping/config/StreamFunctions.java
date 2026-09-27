package io.mateu.ecdemo1.mapping.config;

import io.mateu.ecdemo1.integration.model.command.MappingCommand;
import io.mateu.ecdemo1.mapping.commands.MappingCommands;
import io.mateu.ecdemo1.mapping.worker.TaskHandlers;
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
import java.util.NoSuchElementException;

import java.util.List;
import java.util.function.Consumer;

/**
 * What the mapping consumes: the engine's tasks, and the commands other services send it. Both on
 * the consumer thread: a failure leaves the offset uncommitted and the message is redelivered — every
 * step is idempotent, and every command is taken once (its inbox).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StreamFunctions {

    final TaskHandlers tasks;
    final StreamBridge streamBridge;
    final MappingCommands commands;
    final TolerantReader reader;

    /**
     * The commands other services send the mapping ({@code mapping-commands}). One that cannot be
     * read, or that the mapping refuses — an equivalence it will not take — is logged and dropped:
     * repeating it would be refused again. Anything else (the database away) is retried.
     */
    @Bean
    public Consumer<Message<byte[]>> consumeMappingCommands() {
        return message -> {
            MappingCommand command;
            try {
                command = reader.mapper().readValue(message.getPayload(), MappingCommand.class);
            } catch (IOException e) {
                log.error("Unreadable mapping command, dropped: {}", new String(message.getPayload()), e);
                return;
            }
            try {
                commands.handle(command);
            } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
                log.error("Mapping command refused, dropped: {} — {}", command, e.getMessage());
            }
        };
    }

    /**
     * The engine's tasks for the mapping. A step that throws is answered as an error, which the
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
