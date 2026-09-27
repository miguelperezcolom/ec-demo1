package io.mateu.ecdemo1.booking.application.usecases.commands;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.mateu.ecdemo1.booking.application.out.inbox.Inbox;
import io.mateu.ecdemo1.booking.application.usecases.booking.pmsreference.AnnotatePmsReferenceCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.pmsreference.AnnotatePmsReferenceUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What other services ask of the CRS without waiting for an answer — its {@code booking-commands}
 * topic: today, the integration writing back where a booking landed in the PMS. Each is taken once:
 * its id goes into the inbox in the same transaction as what it does, and a repetition does nothing.
 *
 * <p>This is the CRS's contract, in its own terms: whoever sends these writes them as they are here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCommands {

    static final String CONSUMER = "booking-commands";

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AnnotatePmsReference.class, name = "annotate-pms-reference"),
    })
    public sealed interface Command {
        String commandId();
    }

    /** Where the booking landed in the PMS, for an operator of the CRS to find it. */
    public record AnnotatePmsReference(String commandId, String bookingId, String pmsReservationId) implements Command {
    }

    final Inbox inbox;
    final AnnotatePmsReferenceUseCase annotatePmsReference;

    /**
     * @return false for a command already taken
     * @throws IllegalArgumentException for one that cannot be carried out (no id, no reservation id)
     */
    @Transactional
    public boolean handle(Command command) {
        if (command.commandId() == null || command.commandId().isBlank()) {
            throw new IllegalArgumentException("A command needs its id: " + command);
        }
        if (!inbox.firstTime(CONSUMER, command.commandId())) {
            log.debug("Already taken: {}", command);
            return false;
        }
        switch (command) {
            case AnnotatePmsReference c -> annotatePmsReference.handle(new AnnotatePmsReferenceCommand(c.bookingId(),
                    c.pmsReservationId()));
        }
        log.info("Taken: {}", command);
        return true;
    }
}
