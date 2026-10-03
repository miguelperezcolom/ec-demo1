package io.mateu.ecdemo1.integrations.demo;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.integrations.audit.Audited;
import io.mateu.ecdemo1.integrations.outbox.Outbox;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.domain.ProcessCancellationRequested;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import io.mateu.workflow.dtos.events.integration.RetryProcessRequested;
import io.mateu.workflow.security.AuthorizationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The demo's reset, as the Demo page asks for it: launched (the engine's reset-demo, which first asks
 * an administrator to confirm it in the inbox), cancelled while it waits, or retried from the step
 * that failed. Each one audited, by who; each one a request to the engine through the outbox.
 */
@Component
@RequiredArgsConstructor
public class DemoResets {

    static final DateTimeFormatter KEY = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    final Outbox outbox;
    final ResetRuns runs;
    final PlatformTransactionManager transactions;
    final Clock clock;

    /** @return the new run's key */
    @Audited("Demo: lanzar el reset")
    public String launch(String by) {
        if (!runs.available()) {
            throw new IllegalStateException("Sin acceso a la base de datos del motor: no se puede saber si hay un reset en curso.");
        }
        runs.open().ifPresent(open -> {
            throw new IllegalStateException("Ya hay un reset en curso (" + open.businessKey() + ", " + open.status()
                    + "): termínalo, reinténtalo o cancélalo antes.");
        });
        var now = clock.instant();
        var key = ResetRuns.DEFINITION + ":" + KEY.format(now) + "-" + UUID.randomUUID().toString().substring(0, 4);
        new TransactionTemplate(transactions).executeWithoutResult(status -> outbox.appendToEngine(new ProcessCreationRequested(
                ResetRuns.DEFINITION, key,
                List.of(new Variable(ProcessVariables.PROCESS_KEY, key),
                        new Variable("launchedBy", by),
                        new Variable("launchedAt", now.toString())),
                null, AuthorizationContext.SYSTEM)));
        return key;
    }

    @Audited("Demo: cancelar el reset")
    public String cancel(String processId, String by) {
        var run = runs.latest().filter(r -> r.processId().equals(processId) && r.open())
                .orElseThrow(() -> new IllegalStateException("Ese reset ya no está en curso."));
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                outbox.appendToEngine(new ProcessCancellationRequested(run.businessKey(), run.processId())));
        return run.businessKey();
    }

    @Audited("Demo: reintentar el reset")
    public String retry(String processId, String by) {
        var run = runs.latest().filter(r -> r.processId().equals(processId) && r.failed())
                .orElseThrow(() -> new IllegalStateException("Ese reset no está en error: no hay nada que reintentar."));
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                outbox.appendToEngine(new RetryProcessRequested(run.processId())));
        return run.businessKey();
    }
}
