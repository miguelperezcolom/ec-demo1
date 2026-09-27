package io.mateu.ecdemo1.booking.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.application.usecases.booking.noshow.RegisterNoShowUseCase;
import io.mateu.ecdemo1.booking.worker.runtime.ExactStrings;
import io.mateu.ecdemo1.booking.worker.runtime.WorkerRuntime;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import io.mateu.workflow.worker.api.Cancellations;
import io.mateu.workflow.worker.api.TaskDispatcher;
import io.mateu.workflow.worker.api.TaskRegistry;
import io.mateu.workflow.worker.api.TaskReplySink;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** «Registrar no-show»'s step, run through the engine's worker runtime as the contract it serves. */
class BookingTasksTest {

    final List<String> noShows = new ArrayList<>();
    final RegisterNoShowUseCase useCase = new RegisterNoShowUseCase(null, null, null) {
        @Override
        public void handle(String bookingId) {
            if ("GONE".equals(bookingId)) {
                throw new IllegalStateException("No booking GONE");
            }
            noShows.add(bookingId);
        }
    };
    final TaskHandlers handlers = new TaskHandlers(useCase);
    final BookingTasks tasks = new BookingTasks();
    final RecordingSink sink = new RecordingSink();
    final TaskDispatcher dispatcher = new TaskDispatcher(new TaskRegistry(List.of(tasks.registerNoShowTask(handlers))),
            sink, Cancellations.NONE, new ExactStrings(new ObjectMapper()), false);
    final WorkerRuntime.LegacyTasks legacy = new WorkerRuntime.LegacyTasks(dispatcher, tasks.legacyTaskRefs());

    static class RecordingSink implements TaskReplySink {
        final List<String> replies = new ArrayList<>();

        @Override
        public void running(TaskExecutionRequested task) {
        }

        @Override
        public void completed(TaskExecutionRequested task, List<Variable> variables) {
            replies.add("COMPLETED " + variables);
        }

        @Override
        public void failed(TaskExecutionRequested task, List<Variable> variables, String reason) {
            replies.add("ERROR " + reason);
        }
    }

    static TaskExecutionRequested task(String taskId, Variable... variables) {
        return new TaskExecutionRequested("TE-1", "PROC-1", "registrar-no-show", "register-no-show", taskId,
                List.of(variables));
    }

    @Test
    void theContractHasItsHandlerOnTheBookingTopic() {
        assertThat(new TaskRegistry(List.of(tasks.registerNoShowTask(handlers))).refs())
                .containsExactly("register-no-show@1");
        assertThat(tasks.registerNoShowTask(handlers).topic()).isEqualTo("booking");
    }

    @Test
    void theNoShowIsRegisteredForTheBookingItNamesAndTheRestOfTheProcessIsIgnored() {
        dispatcher.dispatch(task("register-no-show@1", new Variable("bookingId", "12E45"),
                new Variable("reportedBy", "front office"), new Variable("hotelCode", "MRU01")));

        assertThat(noShows).containsExactly("12E45");
        assertThat(sink.replies).containsExactly("COMPLETED []");
    }

    @Test
    void withoutABookingItFailsWithTheReasonItAlwaysGave() {
        dispatcher.dispatch(task("register-no-show@1"));

        assertThat(noShows).isEmpty();
        assertThat(sink.replies).containsExactly("ERROR Step register-no-show needs a 'bookingId' variable");
    }

    @Test
    void aNoShowTheCrsCannotTakeIsAnsweredAsFailed() {
        dispatcher.dispatch(task("register-no-show@1", new Variable("bookingId", "GONE")));

        assertThat(sink.replies).containsExactly("ERROR No booking GONE");
    }

    @Test
    void aNoShowFromADefinitionThatNamesNoContractRunsAsTheContract() {
        assertThat(legacy.accept(task("", new Variable("bookingId", "LOC1")), null)).isTrue();
        assertThat(legacy.accept(task("register-no-show@1", new Variable("bookingId", "LOC1")), null)).isFalse();

        assertThat(noShows).containsExactly("LOC1");
    }
}
