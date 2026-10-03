package io.mateu.ecdemo1.pmsintegration.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.pmsintegration.config.OperaContext;
import io.mateu.ecdemo1.pmsintegration.write.PackageRules;
import io.mateu.ecdemo1.pmsintegration.write.ReservationPayload;
import io.mateu.ecdemo1.pmsintegration.worker.PmsTasks;
import io.mateu.workflow.worker.api.TaskContext;
import io.mateu.workflow.worker.api.TaskFailure;
import org.junit.jupiter.api.Test;

/** The run's Opera context: swapped at once by new-opera-context, kept in the ConfigMap, never minted twice. */
class OperaContextTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T17:42:10Z"), ZoneOffset.UTC);

    /** A ConfigMap in memory, recording its writes. */
    static class FakeRunConfig implements RunConfig {
        Stored stored;
        final List<Stored> writes = new ArrayList<>();
        RuntimeException failing;

        @Override
        public Optional<Stored> read() {
            return Optional.ofNullable(stored);
        }

        @Override
        public void write(Stored s) {
            if (failing != null) {
                throw failing;
            }
            writes.add(s);
            stored = s;
        }
    }

    final OperaContext context = new OperaContext("ECDEMO1-10012235", "ECDEMO1-10012235");
    final FakeRunConfig runConfig = new FakeRunConfig();
    final NewOperaContext renew = new NewOperaContext(context, runConfig, CLOCK);

    @Test
    void aNewContextIsTakenAtOnceAndWhatIsWrittenToOperaCarriesIt() {
        var payload = new ReservationPayload(new ObjectMapper(),
                new io.mateu.ecdemo1.pmsintegration.config.OhipProperties(null, null, null, null, null, null, null, null, null),
                PackageRules.NONE, context);
        var before = payload.build(io.mateu.ecdemo1.pmsintegration.ReservationPayloadFixtures.reservation(),
                io.mateu.ecdemo1.pmsintegration.ReservationPayloadFixtures.codes(), "XMAR", "G1", null, null, null);
        assertThat(before.toString()).contains("\"idContext\":\"ECDEMO1-10012235\"").contains("\"customReference\":\"ECDEMO1-10012235\"");

        var out = renew.renew("reset-demo:1");

        assertThat(out).isEqualTo(new NewOperaContext.Output("ECDEMO1-10031742", "ECDEMO1-10031742"));
        var after = payload.build(io.mateu.ecdemo1.pmsintegration.ReservationPayloadFixtures.reservation(),
                io.mateu.ecdemo1.pmsintegration.ReservationPayloadFixtures.codes(), "XMAR", "G1", null, null, null);
        assertThat(after.toString()).contains("\"idContext\":\"ECDEMO1-10031742\"").contains("\"customReference\":\"ECDEMO1-10031742\"");
        assertThat(runConfig.writes).containsExactly(new RunConfig.Stored("ECDEMO1-10031742", "ECDEMO1-10031742", "reset-demo:1"));
    }

    @Test
    void theSameProcessGetsTheSameContextBackFromMemory() {
        var first = renew.renew("reset-demo:1");
        var again = new NewOperaContext(context, runConfig, Clock.offset(CLOCK, java.time.Duration.ofHours(2))).renew("reset-demo:1");

        assertThat(again).isEqualTo(first);
        assertThat(runConfig.writes).hasSize(1);
    }

    @Test
    void afterARestartTheSameProcessGetsTheContextTheConfigMapKeeps() {
        runConfig.stored = new RunConfig.Stored("ECDEMO1-10031700", "ECDEMO1-10031700", "reset-demo:1");

        var out = renew.renew("reset-demo:1");

        assertThat(out.operaContext()).isEqualTo("ECDEMO1-10031700");
        assertThat(context.externalSystemCode()).isEqualTo("ECDEMO1-10031700");
        assertThat(runConfig.writes).isEmpty();
    }

    @Test
    void anotherProcessMintsANewOne() {
        runConfig.stored = new RunConfig.Stored("ECDEMO1-10021000", "ECDEMO1-10021000", "reset-demo:0");

        assertThat(renew.renew("reset-demo:1").operaContext()).isEqualTo("ECDEMO1-10031742");
    }

    @Test
    void aWriteThatFailsChangesNothingAndTheTaskFails() throws Exception {
        runConfig.failing = new IllegalStateException("Kubernetes API refused to write ConfigMap ec-demo-run: 403");
        var task = new PmsTasks().newOperaContextTask(renew);

        assertThatThrownBy(() -> task.handler().handle(new PmsTasks.ContextTask("reset-demo:1"), ctx()))
                .isInstanceOf(TaskFailure.class).hasMessageContaining("403");
        assertThat(context.externalSystemCode()).isEqualTo("ECDEMO1-10012235");
        assertThat(task.ref()).isEqualTo("new-opera-context@1");
        assertThat(task.topic()).isEqualTo("pms-integration");
    }

    @Test
    void theLegacyContextKeepsItsOldCustomReference() {
        assertThat(OperaContext.customReferenceFor("ECDEMO1")).isEqualTo("EC-DEMO1");
        assertThat(OperaContext.customReferenceFor("ECDEMO1-10031742")).isEqualTo("ECDEMO1-10031742");
    }

    static TaskContext ctx() {
        return new TaskContext() {
            public String taskExecutionId() { return "t"; }
            public String processId() { return "p"; }
            public String workflowDefinitionId() { return "reset-demo"; }
            public String stepId() { return "new-opera-context"; }
            public boolean isCancelled() { return false; }
            public void progress(String message) { }
        };
    }
}
