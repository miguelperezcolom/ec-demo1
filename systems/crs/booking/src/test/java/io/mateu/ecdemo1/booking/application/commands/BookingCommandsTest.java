package io.mateu.ecdemo1.booking.application.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.application.out.inbox.Inbox;
import io.mateu.ecdemo1.booking.application.usecases.booking.pmsreference.AnnotatePmsReferenceCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.pmsreference.AnnotatePmsReferenceUseCase;
import io.mateu.ecdemo1.booking.application.usecases.commands.BookingCommands;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The commands the CRS takes, each once — without a broker or a database. */
class BookingCommandsTest {

    final List<AnnotatePmsReferenceCommand> annotated = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    final Inbox inbox = (consumer, id) -> seen.add(consumer + "/" + id);
    final BookingCommands commands = new BookingCommands(inbox, new AnnotatePmsReferenceUseCase(null, null) {
        @Override
        public void handle(AnnotatePmsReferenceCommand command) {
            annotated.add(command);
        }
    });

    @Test
    void thePmsReferenceIsWrittenOnceHoweverOftenItIsDelivered() throws Exception {
        var command = new ObjectMapper().readValue("""
                {"type":"annotate-pms-reference","commandId":"TE-1","bookingId":"LOC1","pmsReservationId":"OPERA-77"}""",
                BookingCommands.Command.class);

        assertThat(commands.handle(command)).isTrue();
        assertThat(commands.handle(command)).isFalse();

        assertThat(annotated).containsExactly(new AnnotatePmsReferenceCommand("LOC1", "OPERA-77"));
    }

    @Test
    void aCommandWithNoIdIsRefused() {
        assertThatThrownBy(() -> commands.handle(new BookingCommands.AnnotatePmsReference(null, "LOC1", "OPERA-77")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(annotated).isEmpty();
    }
}
