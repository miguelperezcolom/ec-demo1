package io.mateu.ecdemo1.integrations.demo;

import java.time.Clock;
import java.util.UUID;

import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.integrations.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reset's last word: a notice in the administrators' inbox with what it did — who launched it, who
 * confirmed it, the new Opera context, what went from Salesforce and the engine, the health — and its
 * audit record («Demo: reset completado»). One per run: the notice is keyed by the run (its dedupKey),
 * and the audit record's id is the run's, so a retried step records nothing twice.
 */
@Component
@RequiredArgsConstructor
public class ResetNotice {

    public record Result(String processKey, String launchedBy, String confirmedBy, String operaContext,
                         String salesforceDeleted, String enginePurged, String seededBookings, boolean healthOk,
                         String healthSummary, String link) {
    }

    final Outbox outbox;
    final Clock clock;

    @Transactional
    public void notify(Result r) {
        var now = clock.instant();
        var title = r.healthOk() ? "La demo está a cero" : "La demo está a cero, con avisos";
        var body = new StringBuilder()
                .append("Reset ").append(r.processKey()).append(", lanzado por ").append(or(r.launchedBy()))
                .append(" y confirmado por ").append(or(r.confirmedBy())).append(".\n")
                .append("Contexto de Opera: ").append(or(r.operaContext())).append(".\n")
                .append("Salesforce: ").append(or(r.salesforceDeleted())).append(".\n")
                .append("Motor: ").append(or(r.enginePurged())).append(" proceso(s) borrado(s).\n");
        if (r.seededBookings() != null && !r.seededBookings().isBlank()) {
            body.append("Reservas demo sembradas: ").append(r.seededBookings()).append(".\n");
        }
        body.append("Salud:\n").append(or(r.healthSummary()));
        outbox.appendNotification(new NotificationRequested(UUID.nameUUIDFromBytes(("notice:" + r.processKey()).getBytes()).toString(),
                NotificationType.PLATFORM_ALERT, null, "demo/reset/" + r.processKey(), title, body.toString(), r.link(),
                "demo-reset:" + r.processKey(), now));
        outbox.appendAudit(new AuditedAction(UUID.nameUUIDFromBytes(("audit:" + r.processKey()).getBytes()).toString(), now,
                "integrations", "Demo: reset completado", null, or(r.launchedBy()),
                "{\"processKey\":\"" + r.processKey() + "\",\"confirmedBy\":\"" + or(r.confirmedBy())
                        + "\",\"operaContext\":\"" + or(r.operaContext()) + "\"}",
                true, title));
    }

    static String or(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
