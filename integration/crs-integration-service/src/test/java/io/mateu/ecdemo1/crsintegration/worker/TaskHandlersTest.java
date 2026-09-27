package io.mateu.ecdemo1.crsintegration.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.crsintegration.commands.SystemCommands;
import io.mateu.ecdemo1.crsintegration.outbox.Outbox;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The steps that write back to the CRS and the ERP: commands in the outbox — without a broker or a database. */
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
    final TaskHandlers handlers = new TaskHandlers(outbox, transactions);

    static TaskExecutionRequested task(String step, Variable... variables) {
        return new TaskExecutionRequested("TE-1", "PROC-1", "proyectar-reserva", step, "", List.of(variables));
    }

    @Test
    void thePmsReferenceGoesToTheCrsAsACommandOfTheTask() throws Exception {
        handlers.handlers().get("annotate-pms-reference").apply(task("annotate-pms-reference",
                new Variable("locator", "LOC1"), new Variable("pmsReservationId", "OPERA-77")));

        assertThat(outbox.written).containsExactly(new SystemCommands.AnnotatePmsReference("TE-1", "LOC1", "OPERA-77"));
        assertThat(transactions.committed).isEqualTo(1);
        assertThat(new ObjectMapper().writeValueAsString(outbox.written.getFirst()))
                .contains("\"type\":\"annotate-pms-reference\"").contains("\"bookingId\":\"LOC1\"").doesNotContain("\"key\"");
    }

    @Test
    void thePartnersProfileGoesToTheErpAsACommandOfTheTask() throws Exception {
        handlers.handlers().get("annotate-partner-profile").apply(task("annotate-partner-profile",
                new Variable("partnerCode", "NORDTRAVEL"), new Variable("pmsProfileIds", "16120699"),
                new Variable("pmsProfileType", "Agent")));

        assertThat(outbox.written).containsExactly(new SystemCommands.RecordPmsProfile("TE-1", "NORDTRAVEL", "16120699", "Agent"));
        assertThat(new ObjectMapper().writeValueAsString(outbox.written.getFirst()))
                .contains("\"type\":\"record-pms-profile\"").contains("\"profileId\":\"16120699\"");
    }

    @Test
    void aStepWithoutWhatItNeedsWritesNothing() {
        assertThatThrownBy(() -> handlers.handlers().get("annotate-pms-reference").apply(task("annotate-pms-reference",
                new Variable("locator", "LOC1")))).isInstanceOf(IllegalArgumentException.class);
        assertThat(outbox.written).isEmpty();
    }
}
