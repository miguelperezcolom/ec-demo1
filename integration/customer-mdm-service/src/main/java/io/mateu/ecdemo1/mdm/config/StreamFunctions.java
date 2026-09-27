package io.mateu.ecdemo1.mdm.config;

import io.mateu.ecdemo1.integration.model.command.CustomerCommand;
import io.mateu.ecdemo1.mdm.commands.CustomerCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * What the MDM consumes: the hotels' commands ({@code customer-commands}). On the consumer thread and
 * synchronously: a failure leaves the offset uncommitted and the message is redelivered, which the
 * inbox makes harmless.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StreamFunctions {

    final CustomerCommands commands;
    final TolerantReader reader;

    @Bean
    public Consumer<Message<byte[]>> consumeCustomerCommands() {
        return message -> {
            CustomerCommand command;
            try {
                command = reader.mapper().readValue(message.getPayload(), CustomerCommand.class);
            } catch (IOException e) {
                log.error("Unreadable customer command, dropped: {}", new String(message.getPayload()), e);
                return;
            }
            try {
                commands.handle(command);
            } catch (IllegalArgumentException | NoSuchElementException e) {
                // A customer this MDM does not have, a scan without a document: the same again would be refused again.
                log.error("Customer command refused, dropped: {} — {}", command, e.getMessage());
            }
        };
    }
}
