package io.mateu.ecdemo1.integrations.rest;

import io.mateu.ecdemo1.integrations.store.FoBackfillRun;
import io.mateu.ecdemo1.integrations.store.FoIntegrationStatus;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegration;
import io.mateu.ecdemo1.integrations.store.Integration;

import java.time.Instant;
import java.util.List;

/** A pms-fo integration as it is shown. */
public record FrontOfficeIntegrationDto(String id, String pmsHotelCode, String frontOfficeCode, String name,
                                        String frontOfficeUrl, String scope, int horizonDays, FoIntegrationStatus status,
                                        String waitingFor, Boolean connectivityOk, String connectivityMessage,
                                        String catalogueCommandId, String catalogueSummary, Instant catalogueSyncedAt,
                                        Backfill backfill, String pollCursor, Instant lastPollAt, Integer lastPollChanges,
                                        Instant activatedAt, List<Integration.HistoryEntry> history, long version) {

    public record Backfill(String id, String status, boolean onboarding, int dispatched, Integer expected, int pending,
                           String cursorAtStart, Instant startedAt, Instant finishedAt) {
    }

    public static FrontOfficeIntegrationDto of(FrontOfficeIntegration i, FoBackfillRun run) {
        return new FrontOfficeIntegrationDto(i.id, i.pmsHotelCode, i.frontOfficeCode, i.name, i.frontOfficeUrl,
                i.scope == null ? null : i.scope.name(), i.horizonDays, i.getStatus(), waitingFor(i.gate), i.connectivityOk,
                i.connectivityMessage, i.catalogueCommandId, i.catalogueSummary, i.catalogueSyncedAt,
                run == null ? null : new Backfill(run.id, run.status.name(), run.onboarding, run.dispatched, run.expected,
                        run.pending == null ? 0 : run.pending.size(), run.cursorAtStart, run.startedAt, run.finishedAt),
                i.pollCursor, i.lastPollAt, i.lastPollChanges, i.activatedAt, i.history, i.version == null ? 0 : i.version);
    }

    /** The gate the onboarding waits at, in words. */
    public static String waitingFor(String gate) {
        if (gate == null) {
            return null;
        }
        return switch (gate) {
            case "fo-integration-connectivity-ok" -> "Opera and the front office to answer";
            case "fo-integration-catalogue-synced" -> "The front office to hold the PMS's catalogue";
            case "fo-integration-backfill-done" -> "The backfill to project the property's reservations";
            case "fo-integration-activation-requested" -> "A person to activate it";
            default -> gate;
        };
    }
}
