package io.mateu.ecdemo1.integrations.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.mateu.ecdemo1.integrations.outbox.Outbox;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.domain.ProcessCancellationRequested;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import io.mateu.workflow.dtos.events.integration.RetryProcessRequested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class DemoResetsTest {

    final Outbox outbox = mock(Outbox.class);
    final ResetRuns runs = mock(ResetRuns.class);
    final DemoResets resets = new DemoResets(outbox, runs, new NoTransactions(),
            Clock.fixed(Instant.parse("2026-10-03T17:00:00Z"), ZoneOffset.UTC));

    static ResetRuns.Run run(String status) {
        return new ResetRuns.Run("p1", "reset-demo:x", status, Instant.now(), null, 0, Map.of("launchedBy", "Ana"), null, List.of());
    }

    @Test
    void launchingStartsResetDemoWithWhoAndAKeyOfItsOwn() {
        when(runs.available()).thenReturn(true);
        when(runs.open()).thenReturn(Optional.empty());

        var key = resets.launch("Ana");

        var event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).appendToEngine(event.capture());
        var created = (ProcessCreationRequested) event.getValue();
        assertThat(created.workflowDefinitionId()).isEqualTo("reset-demo");
        assertThat(created.businessKey()).isEqualTo(key).startsWith("reset-demo:20261003-170000-");
        assertThat(created.variables()).contains(new Variable("launchedBy", "Ana"), new Variable("processKey", key));
    }

    @Test
    void aSecondResetWaitsForTheOneInCourse() {
        when(runs.available()).thenReturn(true);
        when(runs.open()).thenReturn(Optional.of(run("RUNNING")));

        assertThatThrownBy(() -> resets.launch("Ana")).hasMessageContaining("Ya hay un reset en curso");
        verify(outbox, never()).appendToEngine(any());
    }

    @Test
    void withoutTheEnginesDatabaseNothingIsLaunched() {
        when(runs.available()).thenReturn(false);

        assertThatThrownBy(() -> resets.launch("Ana")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelAndRetryAskTheEngine() {
        when(runs.latest()).thenReturn(Optional.of(run("ERROR")));

        resets.retry("p1", "Ana");
        resets.cancel("p1", "Ana");

        var event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, org.mockito.Mockito.times(2)).appendToEngine(event.capture());
        assertThat(event.getAllValues().get(0)).isEqualTo(new RetryProcessRequested("p1"));
        assertThat(event.getAllValues().get(1)).isEqualTo(new ProcessCancellationRequested("reset-demo:x", "p1"));
    }

    @Test
    void onlyAFailedRunIsRetried() {
        when(runs.latest()).thenReturn(Optional.of(run("RUNNING")));

        assertThatThrownBy(() -> resets.retry("p1", "Ana")).hasMessageContaining("no está en error");
    }

    static class NoTransactions extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) { }
        protected void doCommit(DefaultTransactionStatus status) { }
        protected void doRollback(DefaultTransactionStatus status) { }
    }
}
