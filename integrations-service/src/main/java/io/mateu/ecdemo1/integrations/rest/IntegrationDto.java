package io.mateu.ecdemo1.integrations.rest;

import io.mateu.ecdemo1.integration.model.integration.Gap;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integrations.store.BackfillRun;
import io.mateu.ecdemo1.integrations.store.Integration;

import java.time.Instant;
import java.util.List;

/** An integration as it is shown — everything but the secret. */
public record IntegrationDto(String id, String crsHotelCode, String pmsHotelCode, String name, IntegrationStatus status,
                             String waitingFor, String gatewayUrl, String appKey, String clientId, String enterpriseId,
                             Boolean connectivityOk, String connectivityMessage, Boolean propertyConfigured,
                             String contrastSummary, Integer pendingMappings, Instant mappingApprovedAt,
                             String mappingApprovedBy, List<String> partnersMissing, List<Gap> gaps,
                             Integer futureReservations, Backfill backfill, Instant availabilitySuspendedSince,
                             Instant activatedAt, List<Integration.HistoryEntry> history, long version) {

    public record Backfill(String id, String status, int dispatched, Integer expected, String windowEnd,
                           boolean windowCovered, String cursorArrival, Instant startedAt, Instant finishedAt) {
    }

    public static IntegrationDto of(Integration i, BackfillRun run) {
        return new IntegrationDto(i.id, i.crsHotelCode, i.pmsHotelCode, i.name, i.status, waitingFor(i.gate), i.gatewayUrl,
                i.appKey, i.clientId, i.enterpriseId, i.connectivityOk, i.connectivityMessage, i.propertyConfigured,
                i.contrastSummary, i.pendingMappings, i.mappingApprovedAt, i.mappingApprovedBy, i.partnersMissing, i.gaps,
                i.futureReservations, run == null ? null : new Backfill(run.id, run.status.name(), run.dispatched,
                run.expected, String.valueOf(run.windowEnd), run.windowCovered, String.valueOf(run.cursorArrival),
                run.startedAt, run.finishedAt), i.availabilitySuspendedSince, i.activatedAt, i.history,
                i.version == null ? 0 : i.version);
    }

    /** The gate the onboarding waits at, in words. */
    public static String waitingFor(String gate) {
        if (gate == null) {
            return null;
        }
        return switch (gate) {
            case "integration-connectivity-ok" -> "A connection Opera takes";
            case "integration-property-configured" -> "The property to be configured in Opera";
            case "integration-mapping-approved" -> "A person to approve the mapping";
            case "integration-partners-synced" -> "The partners to be PMS profiles";
            case "integration-backfill-clear" -> "The backfill's gaps to be resolved";
            case "integration-window-covered" -> "The backfill to cover the activation window";
            case "integration-activation-requested" -> "A person to activate it";
            default -> gate;
        };
    }
}
