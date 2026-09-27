package io.mateu.ecdemo1.booking.infra.in.async;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.application.usecases.commands.BookingCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * The {@code booking-commands} topic, on the consumer thread: a failure leaves the offset uncommitted
 * and the command is redelivered, which the inbox makes harmless. A command that cannot be read, or
 * that the CRS refuses — a booking it does not have — is logged and dropped: repeating it would be
 * refused again.
 */
@Configuration
@Slf4j
public class BookingCommandsConsumer {

    final BookingCommands commands;
    /** Tolerant: a field a sender adds tomorrow must not stop the CRS today. */
    final ObjectMapper reader;

    public BookingCommandsConsumer(BookingCommands commands, ObjectMapper objectMapper) {
        this.commands = commands;
        this.reader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public Consumer<Message<byte[]>> consumeBookingCommands() {
        return message -> {
            BookingCommands.Command command;
            try {
                command = reader.readValue(message.getPayload(), BookingCommands.Command.class);
            } catch (IOException e) {
                log.error("Unreadable booking command, dropped: {}", new String(message.getPayload()), e);
                return;
            }
            try {
                commands.handle(command);
            } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
                log.error("Booking command refused, dropped: {} — {}", command, e.getMessage());
            }
        };
    }
}
