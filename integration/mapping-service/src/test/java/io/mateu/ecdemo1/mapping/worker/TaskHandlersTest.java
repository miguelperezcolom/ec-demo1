package io.mateu.ecdemo1.mapping.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.process.Outcome;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.config.TolerantReader;
import io.mateu.ecdemo1.mapping.prepare.Preparation;
import io.mateu.ecdemo1.mapping.worker.runtime.ExactStrings;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import io.mateu.workflow.worker.api.Cancellations;
import io.mateu.workflow.worker.api.TaskDispatcher;
import io.mateu.workflow.worker.api.TaskRegistry;
import io.mateu.workflow.worker.api.TaskReplySink;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mapping's steps, run through the engine's worker runtime as the contracts they serve — the
 * registrations, the bridge for steps whose definition names no contract, and the replies — without
 * a broker or a database.
 */
class TaskHandlersTest {

    final List<String> resolved = new ArrayList<>();
    final List<Preparation.WaitContext> waits = new ArrayList<>();

    final Causes causes = new Causes(null, null, null, null, null, null) {
        @Override
        public String relaunch(String processKey) {
            return processKey + ">r";
        }

        @Override
        public void resolveIfOpen(String causeKey, String resolvedBy) {
            resolved.add(causeKey + " by " + resolvedBy);
        }
    };

    final IntegrationClients clients = new IntegrationClients(
            new MappingProperties(null, null, null, null, null, Duration.ofSeconds(1)), new TolerantReader(new ObjectMapper())) {
        @Override
        public Reservation reservation(String hotelCode, String locator) {
            return new Reservation(hotelCode, locator, 2, null, null, null, null, null, null, null, null,
                    List.of(), List.of(), null, null, null, null, null);
        }
    };

    final Preparation preparation = new Preparation(null, null, null, null) {
        @Override
        public Outcome reservation(Reservation r, WaitContext wait) {
            waits.add(wait);
            return Outcome.values()[0];
        }

        @Override
        public Outcome cancellation(Reservation r, WaitContext wait) {
            waits.add(wait);
            return Outcome.values()[0];
        }
    };

    final TaskHandlers handlers = new TaskHandlers(preparation, causes, clients, null, Clock.systemUTC());
    final MappingTasks tasks = new MappingTasks();
    final RecordingSink sink = new RecordingSink();
    final TaskRegistry registry = new TaskRegistry(List.of(tasks.prepareReservationTask(handlers),
            tasks.prepareCancellationTask(handlers), tasks.preparePartnerTask(handlers),
            tasks.relaunchProcessTask(handlers), tasks.recordPartnerProfileTask(handlers),
            tasks.resolveProjectionTask(handlers)));
    final TaskDispatcher dispatcher = new TaskDispatcher(registry, sink, Cancellations.NONE,
            new ExactStrings(new ObjectMapper()), false);

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

    static TaskExecutionRequested task(String definition, String step, String taskId, Variable... variables) {
        return new TaskExecutionRequested("TE-1", "PROC-1", definition, step, taskId, List.of(variables));
    }

    @Test
    void eachContractHasItsHandler() {
        assertThat(registry.refs()).containsExactly("prepare-reservation@1", "prepare-cancellation@1",
                "prepare-partner@1", "relaunch-process@1", "record-partner-profile@1", "resolve-projection@1");
        assertThat(tasks.relaunchProcessTask(handlers).topic()).isEqualTo("mapping");
    }

    @Test
    void thePrepareOfACancellationWaitsWithTheProcessVariables() {
        dispatcher.dispatch(task("proyectar-cancelacion", "prepare", "prepare-cancellation@1",
                new Variable("hotelCode", "PMI01"), new Variable("locator", "12E45"),
                new Variable("processKey", "proyectar-cancelacion:PMI01/12E45:E-1"),
                new Variable("definitionId", "proyectar-cancelacion"), new Variable("version", "2"),
                new Variable("eventId", "E-1"), new Variable("prepareOutcome", "ignored")));

        assertThat(sink.replies).containsExactly("COMPLETED [Variable[name=prepareOutcome, value=" + Outcome.values()[0].name() + "]]");
        assertThat(waits).singleElement().satisfies(wait -> {
            assertThat(wait.processKey()).isEqualTo("proyectar-cancelacion:PMI01/12E45:E-1");
            assertThat(wait.subject()).isEqualTo("12E45");
            assertThat(wait.origin()).isNull();
            assertThat(wait.variables()).extracting(Variable::name)
                    .containsExactlyInAnyOrder("definitionId", "hotelCode", "locator", "version", "eventId");
        });
    }

    @Test
    void theRelaunchAnswersWithTheSuccessorsKey() {
        dispatcher.dispatch(task("proyectar-reserva", "relaunch-prepare", "relaunch-process@1",
                new Variable("processKey", "K1")));

        assertThat(sink.replies).containsExactly("COMPLETED [Variable[name=successorKey, value=K1>r]]");
    }

    @Test
    void aStepWithoutWhatItNeedsFailsWithTheReasonItAlwaysGave() {
        dispatcher.dispatch(task("proyectar-reserva", "resolve-projection", "resolve-projection@1",
                new Variable("hotelCode", "PMI01")));

        assertThat(resolved).isEmpty();
        assertThat(sink.replies).containsExactly("ERROR Step resolve-projection needs the variable locator");
    }
}
