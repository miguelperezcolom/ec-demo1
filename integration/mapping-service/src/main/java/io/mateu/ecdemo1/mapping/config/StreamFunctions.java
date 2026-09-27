package io.mateu.ecdemo1.mapping.config;

import io.mateu.ecdemo1.integration.model.command.MappingCommand;
import io.mateu.ecdemo1.mapping.commands.MappingCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.util.NoSuchElementException;

import java.util.function.Consumer;

/**
 * What the mapping consumes besides the engine's tasks (those are the worker runtime's: worker.MappingTasks):
 * the commands other services send it. On the consumer thread: a failure leaves the offset uncommitted and the message is redelivered — every
 * step is idempotent, and every command is taken once (its inbox).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StreamFunctions {

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
}
