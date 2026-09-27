package io.mateu.ecdemo1.integrations.audit;

import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integrations.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;

/**
 * The audited action running on this thread, whose record is still to be written. An action does
 * not run in one transaction — what it asks of other services it asks outside any — so its record
 * goes into the transaction that saves what it decided ({@link #recordWithin}), and exists if and
 * only if that commits. {@link AuditAspect} opens the scope around the action and writes the record
 * itself if the action saved nothing, or was refused.
 */
@Component
@RequiredArgsConstructor
public class AuditScope {

    static final ThreadLocal<Pending> CURRENT = new ThreadLocal<>();

    final Outbox outbox;
    final AuditSubjects subjects;

    /** An action being audited. {@code written} once its record is committed. */
    static final class Pending {
        final String actionId;
        final Instant at;
        final String action;
        final String by;
        final Map<String, Object> parameters;
        final String parametersJson;
        volatile boolean written;
        boolean writing;

        Pending(String actionId, Instant at, String action, String by, Map<String, Object> parameters, String parametersJson) {
            this.actionId = actionId;
            this.at = at;
            this.action = action;
            this.by = by;
            this.parameters = parameters;
            this.parametersJson = parametersJson;
        }
    }

    Pending open(Pending pending) {
        var previous = CURRENT.get();
        CURRENT.set(pending);
        return previous;
    }

    void close(Pending previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    /**
     * Inside the transaction that saves what the audited action on this thread decided: its record,
     * carried out, written in that same transaction. Nothing if no action is audited, or its record
     * is already written.
     */
    public void recordWithin(Object result) {
        var pending = CURRENT.get();
        if (pending == null || pending.written || pending.writing
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        outbox.appendAudit(succeeded(pending, result));
        pending.writing = true;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                pending.writing = false;
                if (status == STATUS_COMMITTED) {
                    pending.written = true;
                }
            }
        });
    }

    AuditedAction succeeded(Pending p, Object result) {
        return new AuditedAction(p.actionId, p.at, subjects.service(), p.action, subjects.hotel(p.parameters, result), p.by,
                p.parametersJson, true, subjects.response(result));
    }

    AuditedAction refused(Pending p, Throwable why) {
        return new AuditedAction(p.actionId, p.at, subjects.service(), p.action, subjects.hotel(p.parameters, null), p.by,
                p.parametersJson, false, why.getMessage() == null ? why.getClass().getSimpleName() : why.getMessage());
    }
}
