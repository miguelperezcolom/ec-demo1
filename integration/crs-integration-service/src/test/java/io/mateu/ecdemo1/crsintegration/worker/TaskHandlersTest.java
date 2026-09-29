package io.mateu.ecdemo1.crsintegration.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.crsintegration.commands.SystemCommands;
import io.mateu.ecdemo1.crsintegration.outbox.Outbox;
import io.mateu.workflow.worker.api.Cancellations;
import io.mateu.workflow.worker.api.TaskDispatcher;
import io.mateu.workflow.worker.api.TaskRegistry;
import io.mateu.workflow.worker.api.TaskTracing;
import io.mateu.workflow.worker.api.TaskReplySink;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The steps that write back to the CRS and the ERP, run through the engine's worker runtime as the
 * contracts they serve: commands in the outbox — without a broker or a database.
 */
class TaskHandlersTest {

    static class RecordingOutbox extends Outbox {
        final List<Object> written = new ArrayList<>();

        RecordingOutbox() {
            super(null, null, null);
        }

        @Override
        public void appendToCrs(SystemCommands.AnnotatePmsReference command) {
            written.add(command);
        }

        @Override
        public void appendToPartners(SystemCommands.RecordPmsProfile command) {
            written.add(command);
        }
    }

    static class NoTransactions implements PlatformTransactionManager {
        int committed;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            committed++;
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    final RecordingOutbox outbox = new RecordingOutbox();
    final NoTransactions transactions = new NoTransactions();
    final io.mateu.ecdemo1.crsintegration.noshow.NoShowReports noShows =
            org.mockito.Mockito.mock(io.mateu.ecdemo1.crsintegration.noshow.NoShowReports.class);
    final TaskHandlers handlers = new TaskHandlers(outbox, transactions, noShows);
    final RecordingSink sink = new RecordingSink();
    final CrsTasks tasks = new CrsTasks();
    final TaskDispatcher dispatcher = new TaskDispatcher(new TaskRegistry(List.of(
            tasks.annotatePmsReferenceTask(handlers), tasks.annotatePartnerProfileTask(handlers),
            tasks.reportNoShowTask(handlers))),
            sink, Cancellations.NONE, false, TaskTracing.NOOP);

    /** What the runtime would answer the engine. */
    static class RecordingSink implements TaskReplySink {
        final List<String> replies = new ArrayList<>();

        @Override
        public void running(TaskExecutionRequested task) {
        }

        @Override
        public void completed(TaskExecutionRequested task, List<Variable> variables) {
            replies.add("COMPLETED " + task.taskExecutionId() + " " + variables);
        }

        @Override
        public void failed(TaskExecutionRequested task, List<Variable> variables, String reason) {
            replies.add("ERROR " + task.taskExecutionId() + " " + reason);
        }
    }

    static TaskExecutionRequested task(String step, String taskId, Variable... variables) {
        return new TaskExecutionRequested("TE-1", "PROC-1", "proyectar-reserva", step, taskId, List.of(variables));
    }

    @Test
    void eachContractHasItsHandler() {
        var registry = new TaskRegistry(List.of(tasks.annotatePmsReferenceTask(handlers),
                tasks.annotatePartnerProfileTask(handlers)));

        assertThat(registry.refs()).containsExactly("annotate-pms-reference@1", "annotate-partner-profile@1");
        assertThat(tasks.annotatePmsReferenceTask(handlers).topic()).isEqualTo("crs-integration");
    }

    @Test
    void thePmsReferenceGoesToTheCrsAsACommandOfTheTask() throws Exception {
        dispatcher.dispatch(task("annotate-pms-reference", "annotate-pms-reference@1",
                new Variable("locator", "LOC1"), new Variable("pmsReservationId", "OPERA-77"),
                new Variable("hotelCode", "PMI01"), new Variable("writeOutcome", "DONE")));

        assertThat(outbox.written).containsExactly(new SystemCommands.AnnotatePmsReference("TE-1", "LOC1", "OPERA-77"));
        assertThat(transactions.committed).isEqualTo(1);
        assertThat(sink.replies).containsExactly("COMPLETED TE-1 []");
        assertThat(new ObjectMapper().writeValueAsString(outbox.written.getFirst()))
                .contains("\"type\":\"annotate-pms-reference\"").contains("\"bookingId\":\"LOC1\"").doesNotContain("\"key\"");
    }

    @Test
    void theNoShowThePmsRecordedGoesUpToTheCrs() {
        org.mockito.Mockito.when(noShows.report(org.mockito.ArgumentMatchers.eq("MRU01"), org.mockito.ArgumentMatchers.eq("GSX4AK"),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.mateu.ecdemo1.crsintegration.noshow.NoShowReports.Answer.REPORTED);

        dispatcher.dispatch(new TaskExecutionRequested("TE-1", "PROC-1", "registrar-no-show-pms", "report-no-show",
                "report-no-show@1", List.of(new Variable("hotelCode", "MRU01"), new Variable("locator", "GSX4AK"),
                new Variable("stayId", "GSX4AK"), new Variable("pmsHotelCode", "XMAR"), new Variable("noShowOutcome", "DONE"))));

        assertThat(sink.replies).containsExactly("COMPLETED TE-1 [Variable[name=reportOutcome, value=REPORTED]]");
        org.mockito.Mockito.verify(noShows).report("MRU01", "GSX4AK", "front office MRU01 (stay GSX4AK), recorded in the PMS");
    }

    @Test
    void thePartnersProfileGoesToTheErpAsACommandOfTheTask() throws Exception {
        dispatcher.dispatch(task("annotate-partner-profile", "annotate-partner-profile@1",
                new Variable("partnerCode", "NORDTRAVEL"), new Variable("pmsProfileIds", "16120699"),
                new Variable("pmsProfileType", "Agent")));

        assertThat(outbox.written).containsExactly(new SystemCommands.RecordPmsProfile("TE-1", "NORDTRAVEL", "16120699", "Agent"));
        assertThat(new ObjectMapper().writeValueAsString(outbox.written.getFirst()))
                .contains("\"type\":\"record-pms-profile\"").contains("\"profileId\":\"16120699\"");
    }

    @Test
    void aStepWithoutWhatItNeedsWritesNothingAndFailsWithTheReasonItAlwaysGave() {
        dispatcher.dispatch(task("annotate-pms-reference", "annotate-pms-reference@1", new Variable("locator", "LOC1")));

        assertThat(outbox.written).isEmpty();
        assertThat(sink.replies).containsExactly("ERROR TE-1 Step annotate-pms-reference needs the variable pmsReservationId");
    }

    @Test
    void anIdThatLooksLikeANumberReachesTheHandlerAsItIs() {
        dispatcher.dispatch(task("annotate-pms-reference", "annotate-pms-reference@1",
                new Variable("locator", "12E45"), new Variable("pmsReservationId", "0.50")));

        assertThat(outbox.written).containsExactly(new SystemCommands.AnnotatePmsReference("TE-1", "12E45", "0.50"));
    }

}
