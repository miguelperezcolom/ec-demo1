package io.mateu.ecdemo1.mapping.causes;

import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.mapping.CauseType;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.integration.model.process.Definitions;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
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
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.MessageReceived;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import io.mateu.workflow.security.AuthorizationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * The causes processes wait on, and the processes waiting on each (F012, HLA «Un proceso bloqueado
 * espera, no falla»).
 *
 * <p>The engine gives no loops, so a process does not wait and then go back to preparing. It waits
 * once — on a message correlated by its own key — and when its last cause is resolved it is sent
 * that message, resumes, and starts a fresh instance of itself that reads the reservation again.
 * The message is not buffered by the engine, and it can arrive before the process reaches its wait;
 * so it is sent again, until the process answers by asking to be relaunched.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Causes {

    static final Set<String> RELAUNCH_VARIABLES = Set.of(ProcessVariables.DEFINITION_ID, ProcessVariables.HOTEL_CODE,
            ProcessVariables.LOCATOR, ProcessVariables.PARTNER_CODE, ProcessVariables.VERSION, ProcessVariables.EVENT_ID, ProcessVariables.ORIGIN);

    final CauseRecordRepository causes;
    final WaiterRepository waiters;
    final WaiterCauseRepository links;
    final Outbox outbox;
    final MappingProperties properties;
    final Clock clock;

    /**
     * Registers that a process waits on these causes, opening the ones that are not open. A cause
     * opened here for the first time — or again, after it was resolved — is announced once.
     * Registering the same process twice is harmless: a retried step does exactly that.
     */
    @Transactional
    public void await(String processKey, String definitionId, String hotelCode, String subject,
                      List<Variable> variables, List<Cause> blocking) {
        for (var cause : blocking) {
            var record = causes.findById(cause.key()).orElseGet(() -> {
                var fresh = new CauseRecord();
                fresh.causeKey = cause.key();
                fresh.type = cause.type();
                fresh.hotelCode = hotelCode;
                return fresh;
            });
            if (record.status != CauseStatus.OPEN) {
                record.status = CauseStatus.OPEN;
                record.description = cause.description();
                record.openedAt = clock.instant();
                record.resolvedAt = null;
                record.resolvedBy = null;
                record.openings++;
                causes.save(record);
                announce(record);
            }
            if (!links.existsById(new WaiterCause.Key(processKey, cause.key()))) {
                links.save(new WaiterCause(processKey, cause.key()));
            }
        }
        var waiter = waiters.findById(processKey).orElseGet(Waiter::new);
        if (waiter.getStatus() == null) {
            waiter.setProcessKey(processKey);
            waiter.setDefinitionId(definitionId);
            waiter.setHotelCode(hotelCode);
            waiter.setSubject(subject);
            waiter.setVariables(variables.stream().filter(v -> RELAUNCH_VARIABLES.contains(v.name())).toList());
            waiter.setStatus(WaiterStatus.WAITING);
            waiter.setCreatedAt(clock.instant());
            waiters.save(waiter);
        }
        log.info("{} waits on {}", processKey, blocking.stream().map(Cause::key).toList());
    }

    /** A person, or something that happened, removed the cause. Every process waiting only on it resumes. */
    @Transactional
    public void resolve(String causeKey, String resolvedBy) {
        var record = causes.findById(causeKey).orElseThrow(() -> new NoSuchElementException("No cause " + causeKey));
        if (record.status == CauseStatus.RESOLVED) {
            return;
        }
        record.status = CauseStatus.RESOLVED;
        record.resolvedAt = clock.instant();
        record.resolvedBy = resolvedBy;
        causes.save(record);
        var released = 0;
        for (var waiter : waiters.waitingOn(causeKey)) {
            if (links.openCausesOf(waiter.getProcessKey()) == 0) {
                waiter.setStatus(WaiterStatus.RELEASED);
                waiter.setReleasedAt(clock.instant());
                signal(waiter);
                released++;
            }
        }
        log.info("Cause {} resolved by {}: {} process(es) resume", causeKey, resolvedBy, released);
    }

    /** Resolves, if open, the cause this approval removes — and, for a chain entry, every hotel's. */
    @Transactional
    public void mappingApproved(CodeType type, String hotelCode, String code, String approvedBy) {
        causes.findByTypeAndStatus(CauseType.MISSING_MAPPING, CauseStatus.OPEN).stream()
                .filter(c -> c.causeKey.endsWith("/" + type + "/" + code))
                .filter(c -> hotelCode == null || c.causeKey.equals(Cause.missingMapping(hotelCode, type, code).key()))
                .forEach(c -> resolve(c.causeKey, approvedBy));
    }

    /** Resolves a cause if it is open, and does nothing otherwise — for steps that may run twice. */
    @Transactional
    public void resolveIfOpen(String causeKey, String resolvedBy) {
        causes.findById(causeKey).filter(c -> c.status == CauseStatus.OPEN).ifPresent(c -> resolve(causeKey, resolvedBy));
    }

    /**
     * The resumed process asks for its successor. Idempotent: the successor's business key is
     * derived from the waiting process's, so asking twice starts it once.
     */
    @Transactional
    public String relaunch(String processKey) {
        var waiter = waiters.findById(processKey).orElseThrow(() -> new NoSuchElementException("No waiting process " + processKey));
        var successorKey = processKey + ">r";
        if (waiter.getStatus() != WaiterStatus.RELAUNCHED) {
            var variables = new java.util.ArrayList<>(waiter.getVariables());
            variables.add(new Variable(ProcessVariables.PROCESS_KEY, successorKey));
            outbox.appendToEngine(new ProcessCreationRequested(waiter.getDefinitionId(), successorKey, variables,
                    null, AuthorizationContext.SYSTEM));
            waiter.setStatus(WaiterStatus.RELAUNCHED);
            waiter.setFinishedAt(clock.instant());
            waiters.save(waiter);
            log.info("{} resumed: {} started", processKey, successorKey);
        }
        return successorKey;
    }

    /** The one way out that is not resolving the causes: a person gives up on the process. */
    @Transactional
    public void discard(String processKey, String discardedBy) {
        var waiter = waiters.findById(processKey).orElseThrow(() -> new NoSuchElementException("No waiting process " + processKey));
        waiter.setStatus(WaiterStatus.DISCARDED);
        waiter.setFinishedAt(clock.instant());
        waiter.setFinishedBy(discardedBy);
        waiters.save(waiter);
        log.warn("{} discarded by {}", processKey, discardedBy);
    }

    /** Resends the resume message to released processes that have not answered yet. */
    @Scheduled(fixedDelayString = "${mapping.resend-check:10s}")
    @Transactional
    public void resendSilent() {
        for (var waiter : waiters.releasedAndSilentSince(clock.instant().minus(properties.resendAfter()))) {
            log.debug("Resending the resume message to {}", waiter.getProcessKey());
            signal(waiter);
        }
    }

    private void signal(Waiter waiter) {
        outbox.appendToEngine(new MessageReceived(Definitions.CAUSES_RESOLVED_MESSAGE, waiter.getProcessKey(), List.of()));
        waiter.setLastSignalAt(clock.instant());
        waiters.save(waiter);
    }

    private void announce(CauseRecord cause) {
        outbox.appendNotification(new NotificationRequested(
                UUID.randomUUID().toString(),
                cause.type == CauseType.PMS_REJECTED ? NotificationType.PMS_REJECTED : NotificationType.CAUSE_OPENED,
                cause.hotelCode,
                cause.causeKey,
                "Processes waiting: " + cause.description,
                cause.description + ". Processes that need it wait until it is resolved; resolving it resumes all of them.",
                properties.consoleUrl() + "/mapping/causes",
                "cause-opened:" + cause.causeKey + ":" + cause.openings,
                clock.instant()));
    }
}
