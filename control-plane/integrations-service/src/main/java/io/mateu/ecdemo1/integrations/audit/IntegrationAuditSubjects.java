package io.mateu.ecdemo1.integrations.audit;

import io.mateu.ecdemo1.integrations.lifecycle.Integrations;
import io.mateu.ecdemo1.integrations.store.BackfillRun;
import io.mateu.ecdemo1.integrations.store.Integration;
import io.mateu.ecdemo1.integrations.store.IntegrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** An integration's actions are about its CRS hotel, and answer with what its history recorded. */
@Component
@RequiredArgsConstructor
public class IntegrationAuditSubjects implements AuditSubjects {

    final IntegrationRepository integrations;
    final io.mateu.ecdemo1.integrations.store.FrontOfficeIntegrationRepository frontOffices;

    @Override
    public String service() {
        return "integrations";
    }

    @Override
    public String hotel(Map<String, Object> parameters, Object result) {
        if (result instanceof Integration i) {
            return i.crsHotelCode;
        }
        if (result instanceof BackfillRun run) {
            return run.crsHotelCode;
        }
        // A front office integration's actions are about the front office's hotel.
        if (result instanceof io.mateu.ecdemo1.integrations.store.FrontOfficeIntegration f) {
            return f.frontOfficeCode;
        }
        if (result instanceof io.mateu.ecdemo1.integrations.store.FoBackfillRun run) {
            return frontOffices.findById(run.integrationId).map(f -> f.frontOfficeCode).orElse(null);
        }
        if (parameters.get("r") instanceof Integrations.Registration r) {
            return r.crsHotelCode();
        }
        if (parameters.get("r") instanceof io.mateu.ecdemo1.integrations.frontoffice.FrontOfficeIntegrations.Registration r) {
            return r.frontOfficeCode();
        }
        if (parameters.get("id") instanceof String id) {
            return integrations.findById(id).map(i -> i.crsHotelCode)
                    .or(() -> frontOffices.findById(id).map(f -> f.frontOfficeCode)).orElse(null);
        }
        return null;
    }

    @Override
    public String response(Object result) {
        if (result instanceof Integration i) {
            var last = i.history == null || i.history.isEmpty() ? null : i.history.get(i.history.size() - 1);
            return (last == null ? "" : last.what() + " — ") + "status " + i.getStatus();
        }
        if (result instanceof BackfillRun run) {
            return "Backfill " + run.id + " " + run.status;
        }
        if (result instanceof io.mateu.ecdemo1.integrations.store.FrontOfficeIntegration f) {
            var last = f.history == null || f.history.isEmpty() ? null : f.history.get(f.history.size() - 1);
            return (last == null ? "" : last.what() + " — ") + "status " + f.getStatus();
        }
        if (result instanceof io.mateu.ecdemo1.integrations.store.FoBackfillRun run) {
            return "Front office backfill " + run.id + " " + run.status;
        }
        return "Done";
    }
}
