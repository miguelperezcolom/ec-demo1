package io.mateu.ecdemo1.audit.rest;

import io.mateu.ecdemo1.audit.application.AuditQueries;
import io.mateu.ecdemo1.audit.store.AuditRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * A reservation's history, for the services that show it — the front office's «Historial», the CRS's
 * booking —: who did what on a stay or a CRS locator, and when, newest first. Inside the cluster only:
 * the gateway routes no {@code /audit} path (the trail's screens are {@code /_audit}, behind ai-admin).
 */
@RestController
public class AuditController {

    /** One action on the reservation, as a history shows it. */
    public record Entry(Instant at, String service, String action, String by, boolean succeeded, String response,
                        String parameters) {

        static Entry of(AuditRecord r) {
            return new Entry(r.at, r.service, r.action, r.actor, r.succeeded, r.response, r.parameters);
        }
    }

    final AuditQueries queries;

    public AuditController(AuditQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/audit")
    public List<Entry> ofReservation(@RequestParam(required = false) String stayId,
                                     @RequestParam(required = false) String locator,
                                     @RequestParam(defaultValue = "50") int limit) {
        return queries.ofReservation(stayId, locator, limit).stream().map(Entry::of).toList();
    }
}
