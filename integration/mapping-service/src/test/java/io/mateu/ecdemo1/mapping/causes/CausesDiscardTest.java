package io.mateu.ecdemo1.mapping.causes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.mapping.audit.AuditAspect;
import io.mateu.ecdemo1.mapping.audit.MappingAuditSubjects;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.outbox.Outbox;
import io.mateu.ecdemo1.mapping.store.CauseRecord;
import io.mateu.ecdemo1.mapping.store.CauseRecordRepository;
import io.mateu.ecdemo1.mapping.store.CauseStatus;
import io.mateu.ecdemo1.mapping.store.Waiter;
import io.mateu.ecdemo1.mapping.store.WaiterCause;
import io.mateu.ecdemo1.mapping.store.WaiterCauseRepository;
import io.mateu.ecdemo1.mapping.store.WaiterRepository;
import io.mateu.ecdemo1.mapping.store.WaiterStatus;
import io.mateu.workflow.dtos.events.domain.ProcessCancellationRequested;
import io.mateu.workflow.dtos.events.integration.MessageReceived;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Discarding a waiting process (F012): what it changes, what it asks the engine, what it audits — and what it stops. */
class CausesDiscardTest {

    static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    static final String KEY = "proyectar-cancelacion:MRU01/8MU24N:E-1";
    static final String CAUSE = "NOT_YET_PROJECTED:MRU01:8MU24N";

    final CauseRecordRepository causeRecords = mock(CauseRecordRepository.class);
    final WaiterRepository waiters = mock(WaiterRepository.class);
    final WaiterCauseRepository links = mock(WaiterCauseRepository.class);
    final Outbox outbox = mock(Outbox.class);
    final MappingProperties properties = new MappingProperties(null, null, null, null, "https://console.test",
            Duration.ofSeconds(30), Duration.ofHours(6), "https://data.test");
    final Causes causes = new Causes(causeRecords, waiters, links, outbox, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    Waiter waiter;

    @BeforeEach
    void aProcessWaitsOnOneOpenCause() {
        waiter = new Waiter();
        waiter.setProcessKey(KEY);
        waiter.setDefinitionId("proyectar-cancelacion");
        waiter.setHotelCode("MRU01");
        waiter.setStatus(WaiterStatus.WAITING);
        waiter.setVariables(List.of());
        waiter.setEngineProcessId("21c96d08");
        when(waiters.findById(KEY)).thenAnswer(i -> Optional.of(waiter));
        when(links.findByProcessKey(KEY)).thenReturn(List.of(new WaiterCause(KEY, CAUSE)));
        var cause = new CauseRecord();
        cause.causeKey = CAUSE;
        cause.hotelCode = "MRU01";
        cause.status = CauseStatus.OPEN;
        when(causeRecords.findById(CAUSE)).thenReturn(Optional.of(cause));
    }

    @Test
    void discardingGivesTheProcessUpWithWhoAndWhyAndAsksTheEngineToCancelIt() {
        when(waiters.countWaitingOn(CAUSE)).thenReturn(0L);

        var discarded = causes.discard(KEY, "  Test booking, cancelled before it reached the PMS ", "Ana García");

        assertThat(waiter.getStatus()).isEqualTo(WaiterStatus.DISCARDED);
        assertThat(waiter.getFinishedBy()).isEqualTo("Ana García");
        assertThat(waiter.getFinishedAt()).isEqualTo(NOW);
        assertThat(waiter.getReason()).isEqualTo("Test booking, cancelled before it reached the PMS");
        verify(waiters).save(waiter);
        var sent = ArgumentCaptor.forClass(io.mateu.workflow.ddd.DomainEvent.class);
        verify(outbox).appendToEngine(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new ProcessCancellationRequested(KEY, "21c96d08"));
        // Keyed as the engine's own UI keys it: by the process's id, so the owning pod handles it.
        assertThat(sent.getValue().partitionKey()).isEqualTo("21c96d08");
        assertThat(discarded.engineCancelRequested()).isTrue();
        assertThat(discarded.causesLeftUnwaited()).containsExactly(CAUSE);
    }

    @Test
    void aCauseSomeoneElseStillWaitsOnIsNotOfferedForResolving() {
        when(waiters.countWaitingOn(CAUSE)).thenReturn(1L);

        assertThat(causes.discard(KEY, "why", "Ana").causesLeftUnwaited()).isEmpty();
    }

    @Test
    void withoutTheEnginesIdNothingIsSentAndTheOperatorIsSentToAdminProcesses() {
        waiter.setEngineProcessId(null);

        var discarded = causes.discard(KEY, "why", "Ana");

        assertThat(waiter.getStatus()).isEqualTo(WaiterStatus.DISCARDED);
        verify(outbox, never()).appendToEngine(any());
        assertThat(discarded.engineCancelRequested()).isFalse();
        assertThat(discarded.adminProcessesUrl()).isEqualTo("https://data.test/workflow/processes");
    }

    @Test
    void discardingTwiceCancelsOnceAndKeepsTheFirstWhoAndWhy() {
        causes.discard(KEY, "first", "Ana");
        causes.discard(KEY, "second", "Luis");

        assertThat(waiter.getFinishedBy()).isEqualTo("Ana");
        assertThat(waiter.getReason()).isEqualTo("first");
        verify(outbox).appendToEngine(any());
    }

    @Test
    void aProcessThatAlreadyResumedCannotBeDiscardedAndOneNeedsAReason() {
        assertThatThrownBy(() -> causes.discard(KEY, " ", "Ana")).isInstanceOf(IllegalArgumentException.class);
        waiter.setStatus(WaiterStatus.RELAUNCHED);
        assertThatThrownBy(() -> causes.discard(KEY, "why", "Ana")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already resumed");
        verify(outbox, never()).appendToEngine(any());
    }

    @Test
    void aDiscardedProcessThatResumesAnywayStartsNoSuccessor() {
        causes.discard(KEY, "why", "Ana");

        causes.relaunch(KEY);

        verify(outbox, never()).appendToEngine(any(ProcessCreationRequested.class));
        assertThat(waiter.getStatus()).isEqualTo(WaiterStatus.DISCARDED);
    }

    @Test
    void resendingAsksOnlyForReleasedProcessesAndOnlyWithinTheResendWindow() {
        var released = new Waiter();
        released.setProcessKey("K-released");
        released.setStatus(WaiterStatus.RELEASED);
        when(waiters.releasedAndSilentSince(any(), any())).thenReturn(List.of(released));

        causes.resendSilent();

        // Silent for resend-after, released within resend-for: a DISCARDED one is not RELEASED, and one
        // released longer ago than that is taken as cancelled or finished in the engine.
        verify(waiters).releasedAndSilentSince(NOW.minusSeconds(30), NOW.minus(Duration.ofHours(6)));
        var sent = ArgumentCaptor.forClass(io.mateu.workflow.ddd.DomainEvent.class);
        verify(outbox).appendToEngine(sent.capture());
        assertThat(sent.getValue()).isInstanceOfSatisfying(MessageReceived.class,
                m -> assertThat(m.correlationKey()).isEqualTo("K-released"));
    }

    @Test
    void resolvingACauseDoesNotReleaseAProcessDiscardedFromIt() {
        causes.discard(KEY, "why", "Ana");
        // What resolve() resumes is waitingOn(), WAITING only: the discarded one is not among them.
        when(waiters.waitingOn(CAUSE)).thenReturn(List.of());

        causes.resolve(CAUSE, "Ana");

        assertThat(waiter.getStatus()).isEqualTo(WaiterStatus.DISCARDED);
        verify(outbox, never()).appendToEngine(any(MessageReceived.class));
    }

    @Test
    void aDiscardIsAuditedWithItsReasonAndWhoDidIt() throws Exception {
        var auditOutbox = mock(Outbox.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var subjects = new MappingAuditSubjects(null, causeRecords, waiters);
        var factory = new AspectJProxyFactory(causes);
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(auditOutbox, subjects, new ObjectMapper(), transactions, Clock.fixed(NOW, ZoneOffset.UTC)));
        Causes audited = factory.getProxy();

        audited.discard(KEY, "Test booking", "Ana García");

        var action = ArgumentCaptor.forClass(AuditedAction.class);
        verify(auditOutbox).appendAudit(action.capture());
        assertThat(action.getValue().action()).isEqualTo("Discard process");
        assertThat(action.getValue().by()).isEqualTo("Ana García");
        assertThat(action.getValue().hotelCode()).isEqualTo("MRU01");
        assertThat(action.getValue().succeeded()).isTrue();
        assertThat(action.getValue().parameters()).contains(KEY).contains("Test booking");
        assertThat(action.getValue().response()).contains("engine cancellation requested");
    }

    @Test
    void aRefusedDiscardIsAuditedToo() {
        waiter.setStatus(WaiterStatus.RELAUNCHED);
        var auditOutbox = mock(Outbox.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var factory = new AspectJProxyFactory(causes);
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(auditOutbox, new MappingAuditSubjects(null, causeRecords, waiters),
                new ObjectMapper(), transactions, Clock.fixed(NOW, ZoneOffset.UTC)));
        Causes audited = factory.getProxy();

        assertThatThrownBy(() -> audited.discard(KEY, "why", "Luis")).isInstanceOf(IllegalStateException.class);

        var action = ArgumentCaptor.forClass(AuditedAction.class);
        verify(auditOutbox).appendAudit(action.capture());
        assertThat(action.getValue().succeeded()).isFalse();
        assertThat(action.getValue().by()).isEqualTo("Luis");
    }

    @Test
    void discardingEveryProcessOnACauseDiscardsWaitingAndSilentOnes() {
        var other = new Waiter();
        other.setProcessKey("K2");
        other.setStatus(WaiterStatus.RELEASED);
        when(waiters.findById("K2")).thenReturn(Optional.of(other));
        when(waiters.pendingOn(CAUSE)).thenReturn(List.of(waiter, other));

        var all = causes.discardAllWaitingOn(CAUSE, "Test data", "Ana");

        assertThat(all).extracting(Causes.Discarded::processKey).containsExactly(KEY, "K2");
        assertThat(other.getStatus()).isEqualTo(WaiterStatus.DISCARDED);
        verify(outbox).appendToEngine(new ProcessCancellationRequested(KEY, "21c96d08"));
    }
}
