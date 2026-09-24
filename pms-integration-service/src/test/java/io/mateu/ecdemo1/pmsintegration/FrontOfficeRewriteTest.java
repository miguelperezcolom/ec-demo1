package io.mateu.ecdemo1.pmsintegration;

import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.pmsintegration.frontoffice.FrontOfficeWriter;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import io.mateu.workflow.dtos.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A version Opera already held is not written into the front office again — but for a backfill or an MDM merge. */
class FrontOfficeRewriteTest {

    static TaskExecutionRequested task(String outcome, String origin) {
        return new TaskExecutionRequested("p", "proyectar-reserva", "write-front-office", "pms-integration", "t",
                List.of(new Variable(ProcessVariables.WRITE_OUTCOME, outcome), new Variable(ProcessVariables.ORIGIN, origin)));
    }

    @Test
    void aVersionOperaAlreadyHeldIsNotWrittenAgain() {
        assertThat(FrontOfficeWriter.alreadyWritten(task("STALE", "evt-123"))).isTrue();
    }

    @Test
    void aNewVersionIsWritten() {
        assertThat(FrontOfficeWriter.alreadyWritten(task("DONE", "evt-123"))).isFalse();
    }

    @Test
    void aBackfillAndAnMdmMergeWriteItAgain() {
        assertThat(FrontOfficeWriter.alreadyWritten(task("STALE", "backfill:6b85"))).isFalse();
        assertThat(FrontOfficeWriter.alreadyWritten(task("STALE", "mdm-merge-C-1"))).isFalse();
    }
}
