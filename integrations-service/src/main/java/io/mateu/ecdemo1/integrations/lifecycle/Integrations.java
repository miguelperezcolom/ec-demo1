package io.mateu.ecdemo1.integrations.lifecycle;

import io.mateu.ecdemo1.integrations.audit.Audited;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.integration.PmsProperty;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.integration.model.process.Definitions;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.integrations.clients.Services;
import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import io.mateu.ecdemo1.integrations.crypto.SecretBox;
import io.mateu.ecdemo1.integrations.outbox.Outbox;
import io.mateu.ecdemo1.integrations.store.BackfillRun;
import io.mateu.ecdemo1.integrations.store.BackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.Integration;
import io.mateu.ecdemo1.integrations.store.IntegrationRepository;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.MessageReceived;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import io.mateu.workflow.security.AuthorizationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The integration aggregate's behaviour: what a person does to a hotel's integration, and what each
 * step of its onboarding does (HLA F010, «Alta de Integración»).
 *
 * <p>The onboarding is a process of the engine that goes through gates. Each step here does its
 * work, records the outcome on the integration and names the gate the process waits at next; the
 * gate opens when what it waits for is recorded — by a person, or by looking again — and
 * {@link Gates} sends the message that moves the process on. Nothing flows to the PMS until the
 * integration is {@link IntegrationStatus#ACTIVE}: the preparation of every projection holds a
 * hotel whose integration is not, on the cause {@code INTEGRATION_INACTIVE:<hotel>} that
 * activating resolves.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Integrations {

    public record Registration(String crsHotelCode, String pmsHotelCode, String name, String gatewayUrl,
                               String appKey, String clientId, String clientSecret, String enterpriseId) {
    }

    public record ConnectionChange(String gatewayUrl, String appKey, String clientId, String clientSecret,
                                   String enterpriseId) {
    }

    final IntegrationRepository integrations;
    final BackfillRunRepository runs;
    final Services services;
    final SecretBox secrets;
    final Outbox outbox;
    final IntegrationsProperties properties;
    final Clock clock;

    // ── what a person does ──────────────────────────────────────────────────

    /**
     * The chain's connection to Opera, from configuration: what a new integration starts from, and
     * what lists the tenant's properties before any integration exists (HLA R24 — one tenant for
     * the chain). Its property code is blank: that is what each integration names.
     */
    public OhipConnection chainConnection() {
        var opera = properties.opera();
        return new OhipConnection(null, opera.gatewayUrl(), opera.appKey(), opera.clientId(), opera.clientSecret(),
                opera.enterpriseId());
    }

    /** The properties of the chain in Opera, for whoever is registering an integration. */
    public List<PmsProperty> operaProperties() {
        return services.operaProperties(chainConnection());
    }

    /** Registers the integration and starts its onboarding. One per CRS hotel. */
    @Audited("Register integration")
    @Transactional
    public Integration register(Registration r, String by) {
        var chain = chainConnection();
        var registration = new Registration(r.crsHotelCode(), r.pmsHotelCode(), r.name(), blankOr(r.gatewayUrl(), chain.gatewayUrl()),
                blankOr(r.appKey(), chain.appKey()), blankOr(r.clientId(), chain.clientId()),
                blankOr(r.clientSecret(), chain.clientSecret()), blankOr(r.enterpriseId(), chain.enterpriseId()));
        return registerFilled(registration, by);
    }

    @Transactional
    Integration registerFilled(Registration r, String by) {
        require(r.crsHotelCode(), "the CRS hotel");
        require(r.pmsHotelCode(), "the Opera property");
        require(r.gatewayUrl(), "the OHIP gateway");
        require(r.clientSecret(), "the client secret");
        integrations.findByCrsHotelCode(r.crsHotelCode()).ifPresent(existing -> {
            throw new IllegalStateException("Hotel %s already has an integration (%s, %s)"
                    .formatted(r.crsHotelCode(), existing.id, existing.status));
        });
        var i = new Integration();
        i.id = UUID.randomUUID().toString();
        i.crsHotelCode = r.crsHotelCode().trim();
        i.pmsHotelCode = r.pmsHotelCode().trim();
        i.name = r.name();
        i.gatewayUrl = r.gatewayUrl().trim();
        i.appKey = r.appKey();
        i.clientId = r.clientId();
        i.clientSecretSealed = secrets.seal(r.clientSecret());
        i.enterpriseId = r.enterpriseId();
        i.status = IntegrationStatus.CREATED;
        i.processKey = Definitions.ONBOARD_INTEGRATION + ":" + i.id;
        i.createdAt = clock.instant();
        i.createdBy = by;
        i.record(clock.instant(), by, "Registered: CRS %s ↔ Opera %s".formatted(i.crsHotelCode, i.pmsHotelCode));
        integrations.save(i);
        outbox.appendToEngine(new ProcessCreationRequested(Definitions.ONBOARD_INTEGRATION, i.processKey, List.of(
                new Variable(ProcessVariables.PROCESS_KEY, i.processKey),
                new Variable(ProcessVariables.INTEGRATION_ID, i.id),
                new Variable(ProcessVariables.HOTEL_CODE, i.crsHotelCode)), null, AuthorizationContext.SYSTEM));
        log.info("Integration {} registered for {} ↔ {}: onboarding {}", i.id, i.crsHotelCode, i.pmsHotelCode, i.processKey);
        return i;
    }

    /**
     * Changes how to reach the property — a blank secret keeps the one stored — and tries the
     * connection again at once: the usual way out of {@link IntegrationStatus#CONNECTIVITY_FAILED}.
     */
    @Audited("Change connection")
    @Transactional
    public Integration changeConnection(String id, ConnectionChange c, String by) {
        var i = find(id);
        i.gatewayUrl = blankOr(c.gatewayUrl(), i.gatewayUrl);
        i.appKey = blankOr(c.appKey(), i.appKey);
        i.clientId = blankOr(c.clientId(), i.clientId);
        i.enterpriseId = blankOr(c.enterpriseId(), i.enterpriseId);
        if (c.clientSecret() != null && !c.clientSecret().isBlank()) {
            i.clientSecretSealed = secrets.seal(c.clientSecret());
        }
        i.record(clock.instant(), by, "Connection changed" + (c.clientSecret() == null || c.clientSecret().isBlank()
                ? "" : " (new secret)"));
        verify(i, by);
        return integrations.save(i);
    }

    /** Tries the connection again, now. */
    @Audited("Verify connection")
    @Transactional
    public Integration verifyNow(String id, String by) {
        var i = find(id);
        verify(i, by);
        return integrations.save(i);
    }

    /** Looks again at whatever the current gate needs from outside: the catalogue, the partners, the gaps. */
    @Audited("Recheck")
    @Transactional
    public Integration recheck(String id, String by) {
        var i = find(id);
        recheck(i);
        return integrations.save(i);
    }

    /**
     * The mapping gate, opened by hand: a person says the hotel's codes are mapped well enough to go
     * on, some still pending. The backfill's pre-pass insists on the ones the reservations really use.
     */
    @Audited("Approve mapping")
    @Transactional
    public Integration approveMapping(String id, String by) {
        var i = find(id);
        if (i.status != IntegrationStatus.MAPPING_PENDING) {
            throw new IllegalStateException("The mapping is approved while the integration waits for it; it is " + i.status);
        }
        i.mappingApprovedAt = clock.instant();
        i.mappingApprovedBy = by;
        i.record(clock.instant(), by, "Mapping approved (%s code(s) still without equivalence)".formatted(i.pendingMappings));
        return integrations.save(i);
    }

    /** The activation gate: a person switches real-time traffic on. */
    @Audited("Activate integration")
    @Transactional
    public Integration activate(String id, String by) {
        var i = find(id);
        if (i.status != IntegrationStatus.READY_TO_ACTIVATE) {
            throw new IllegalStateException("Only an integration ready to activate can be activated; it is " + i.status);
        }
        i.activationRequestedAt = clock.instant();
        i.activationRequestedBy = by;
        i.record(clock.instant(), by, "Activation requested");
        return integrations.save(i);
    }

    /** Real-time traffic of the hotel waits — every process on one cause — until it is resumed. */
    @Audited("Pause integration")
    @Transactional
    public Integration pause(String id, String by) {
        var i = find(id);
        if (i.status != IntegrationStatus.ACTIVE) {
            throw new IllegalStateException("Only an active integration can be paused; it is " + i.status);
        }
        i.status = IntegrationStatus.PAUSED;
        i.pausedAt = clock.instant();
        i.pausedBy = by;
        i.record(clock.instant(), by, "Paused");
        return integrations.save(i);
    }

    /** Real-time traffic flows again, starting with what waited while the integration was paused. */
    @Audited("Resume integration")
    @Transactional
    public Integration resume(String id, String by) {
        var i = find(id);
        if (i.status != IntegrationStatus.PAUSED) {
            throw new IllegalStateException("Only a paused integration can be resumed; it is " + i.status);
        }
        i.status = IntegrationStatus.ACTIVE;
        i.record(clock.instant(), by, "Resumed");
        integrations.save(i);
        services.resolveCauseIfOpen(Cause.integrationInactive(i.crsHotelCode).key(), by);
        return i;
    }

    /**
     * Takes the integration down. What reached the PMS stays there — deciding about it is the
     * migration's rollback strategy (R5), not this. A half-done onboarding stays where it was: its
     * gates never open again.
     */
    @Audited("Decommission integration")
    @Transactional
    public Integration decommission(String id, String by) {
        var i = find(id);
        i.status = IntegrationStatus.DECOMMISSIONED;
        i.gate = null;
        i.decommissionedAt = clock.instant();
        i.decommissionedBy = by;
        runs.findByStatus(BackfillRun.Status.RUNNING).stream().filter(r -> r.integrationId.equals(i.id)).forEach(r -> {
            r.status = BackfillRun.Status.STOPPED;
            r.finishedAt = clock.instant();
            runs.save(r);
        });
        i.record(clock.instant(), by, "Decommissioned");
        return integrations.save(i);
    }

    /** A backfill on demand (F013): after a long stop, or to recover a gap. Real-time traffic goes on meanwhile. */
    @Audited("Relaunch backfill")
    @Transactional
    public BackfillRun relaunchBackfill(String id, String by) {
        var i = find(id);
        if (i.status != IntegrationStatus.ACTIVE && i.status != IntegrationStatus.PAUSED) {
            throw new IllegalStateException("A backfill is relaunched on a running integration; this one is " + i.status
                    + " — its onboarding runs its own");
        }
        return startBackfill(i, false, by);
    }

    // ── the onboarding's steps ──────────────────────────────────────────────

    /** «Registrar y verificar conectividad». */
    @Transactional
    public void stepVerifyConnectivity(String id) {
        var i = find(id);
        verify(i, "onboarding");
        i.gate = Definitions.GATE_CONNECTIVITY;
        integrations.save(i);
    }

    /** «Verificar el estado inicial»: the property's catalogue against the CRS's. */
    @Transactional
    public void stepContrastCatalogues(String id) {
        var i = find(id);
        contrast(i);
        i.gate = Definitions.GATE_CONFIGURED;
        integrations.save(i);
    }

    /**
     * «Mapeado»: asks the mapping agent for a proposal of whatever is pending, and waits. The gate
     * opens when a person has approved every equivalence the hotel needs — or when a person says to
     * go on regardless ({@link #approveMapping}).
     */
    @Transactional
    public void stepRequestMapping(String id) {
        var i = find(id);
        i.pendingMappings = services.pendingMappings(i.crsHotelCode).size();
        if (i.status != IntegrationStatus.MAPPING_PENDING) {
            transition(i, IntegrationStatus.MAPPING_PENDING, "Waiting for the mapping: %d code(s) pending".formatted(i.pendingMappings));
        }
        if (i.pendingMappings > 0) {
            try {
                services.requestAgentProposal(i.crsHotelCode);
                i.record(clock.instant(), "onboarding", "Asked the mapping agent for a proposal of the %d pending code(s)"
                        .formatted(i.pendingMappings));
            } catch (RuntimeException e) {
                i.record(clock.instant(), "onboarding", "The mapping agent could not be asked (" + e.getMessage()
                        + "); the codes can be mapped by hand, or proposed from Mapping → Pending");
            }
        }
        i.gate = Definitions.GATE_MAPPING;
        integrations.save(i);
    }

    /**
     * «Sincronizar interlocutores»: the partners the hotel's future reservations reference (R12: the
     * subset the property needs, not the whole master) that are not yet profiles in the PMS are
     * announced again, and projected by «Proyectar Interlocutor». Before the backfill, or it fails in
     * cascade.
     */
    @Transactional
    public void stepSyncPartners(String id) {
        var i = find(id);
        transition(i, IntegrationStatus.SYNCING_PARTNERS, "Syncing the partners of the hotel's future reservations");
        var missing = missingPartners(i);
        missing.forEach(services::resyncPartner);
        i.partnersMissing = missing;
        i.record(clock.instant(), "onboarding", missing.isEmpty() ? "Every partner is already a PMS profile"
                : "Announced again for projection: " + String.join(", ", missing));
        i.gate = Definitions.GATE_PARTNERS;
        integrations.save(i);
    }

    /** The backfill's pre-pass: what its reservations really use and still lack. A gate if anything. */
    @Transactional
    public void stepBackfillPrePass(String id) {
        var i = find(id);
        gaps(i);
        if (!i.gaps.isEmpty()) {
            transition(i, IntegrationStatus.BACKFILL_BLOCKED, "The backfill waits: %d gap(s) — %s"
                    .formatted(i.gaps.size(), describeGaps(i)));
        } else {
            i.record(clock.instant(), "onboarding", "Pre-pass clear: %d future reservation(s) to project".formatted(i.futureReservations));
        }
        i.gate = Definitions.GATE_BACKFILL_CLEAR;
        integrations.save(i);
    }

    /** «Backfill»: suspends the property's availability and starts projecting, nearest arrival first. */
    @Transactional
    public void stepStartBackfill(String id) {
        var i = find(id);
        var existing = runs.findFirstByIntegrationIdOrderByStartedAtDesc(i.id)
                .filter(r -> r.onboarding && r.status != BackfillRun.Status.STOPPED);
        if (existing.isEmpty()) {
            startBackfill(i, true, "onboarding");
        }
        i.gate = Definitions.GATE_WINDOW;
        integrations.save(i);
    }

    /** The window is covered: the hotel can be activated, when a person says so. */
    @Transactional
    public void stepAwaitActivation(String id) {
        var i = find(id);
        if (i.status == IntegrationStatus.BACKFILLING) {
            transition(i, IntegrationStatus.READY_TO_ACTIVATE, "Ready to activate: the next %d days are in the PMS"
                    .formatted(properties.activationWindowDays()));
        }
        i.gate = Definitions.GATE_ACTIVATION;
        integrations.save(i);
    }

    /** «Activar»: real-time traffic flows, starting with what waited for this. */
    @Transactional
    public void stepActivate(String id) {
        var i = find(id);
        if (i.status == IntegrationStatus.READY_TO_ACTIVATE) {
            i.status = IntegrationStatus.ACTIVE;
            i.activatedAt = clock.instant();
            i.record(clock.instant(), i.activationRequestedBy, "Active: real-time traffic flows");
        }
        i.gate = null;
        integrations.save(i);
        services.resolveCauseIfOpen(Cause.integrationInactive(i.crsHotelCode).key(), "integration " + i.crsHotelCode);
    }

    // ── gates ───────────────────────────────────────────────────────────────

    /** Whether what the integration's current gate waits for has happened. */
    public boolean gateOpen(Integration i) {
        if (i.gate == null) {
            return false;
        }
        return switch (i.gate) {
            case Definitions.GATE_CONNECTIVITY -> Boolean.TRUE.equals(i.connectivityOk);
            case Definitions.GATE_CONFIGURED -> Boolean.TRUE.equals(i.propertyConfigured);
            case Definitions.GATE_MAPPING -> i.mappingApprovedAt != null
                    || (i.pendingMappings != null && i.pendingMappings == 0);
            case Definitions.GATE_PARTNERS -> i.partnersMissing == null || i.partnersMissing.isEmpty();
            case Definitions.GATE_BACKFILL_CLEAR -> i.gaps == null || i.gaps.isEmpty();
            case Definitions.GATE_WINDOW -> runs.findFirstByIntegrationIdOrderByStartedAtDesc(i.id)
                    .map(r -> r.onboarding && r.windowCovered).orElse(false);
            case Definitions.GATE_ACTIVATION -> i.activationRequestedAt != null;
            default -> false;
        };
    }

    /** Looks again at what a gate needs from outside. Nothing for the gates a person opens. */
    void recheck(Integration i) {
        if (i.status == IntegrationStatus.DECOMMISSIONED) {
            return;
        }
        switch (i.status) {
            case CONNECTIVITY_FAILED -> verify(i, "recheck");
            case PENDING_CONFIGURATION -> contrast(i);
            case MAPPING_PENDING -> {
                var before = i.pendingMappings;
                i.pendingMappings = services.pendingMappings(i.crsHotelCode).size();
                if (i.pendingMappings == 0 && (before == null || before > 0)) {
                    i.record(clock.instant(), "recheck", "Every code of the hotel has an approved equivalence");
                }
            }
            case SYNCING_PARTNERS -> i.partnersMissing = missingPartners(i);
            case BACKFILL_BLOCKED -> gaps(i);
            default -> {
            }
        }
    }

    /** What {@link Gates} looks at on its own, every so often. */
    @Transactional
    public void recheckAutomatically(String id) {
        var i = find(id);
        recheck(i);
        integrations.save(i);
    }

    /** Sends the message that opens the integration's gate: the process moves on, if it is waiting there. */
    @Transactional
    public void signalGate(String id) {
        var i = find(id);
        if (gateOpen(i)) {
            outbox.appendToEngine(new MessageReceived(i.gate, i.processKey, List.of()));
        }
    }

    // ── the work behind the steps ───────────────────────────────────────────

    void verify(Integration i, String by) {
        var check = services.verify(connection(i));
        i.connectivityOk = check.ok();
        i.connectivityMessage = check.message();
        i.connectivityCheckedAt = clock.instant();
        if (check.ok()) {
            services.defineHotel(i.crsHotelCode, i.pmsHotelCode, "integration " + i.crsHotelCode);
            // Which profile type each partner type is in OPERA: the tenant's own types, so entered
            // rather than left pending for a person — creating a partner's profile needs it.
            definePartnerTypes("integration " + i.crsHotelCode);
            if (i.status == IntegrationStatus.CONNECTIVITY_FAILED) {
                transition(i, IntegrationStatus.CREATED, "Connection verified: " + check.message());
            } else {
                i.record(clock.instant(), by, "Connection verified: " + check.message());
            }
        } else if (i.status == IntegrationStatus.CREATED) {
            transition(i, IntegrationStatus.CONNECTIVITY_FAILED, "Opera does not take the connection: " + check.message());
            notifyAttention(i, "Opera does not take the connection of " + i.crsHotelCode,
                    check.message() + ". Fix the connection data or the credentials; the onboarding goes on once they work.");
        }
    }

    void contrast(Integration i) {
        List<CodeEntry> catalog = services.pmsCatalog(i.pmsHotelCode);
        var byType = catalog.stream().filter(e -> i.pmsHotelCode.equals(e.hotelCode()))
                .collect(Collectors.groupingBy(CodeEntry::type, Collectors.counting()));
        var roomTypes = byType.getOrDefault(CodeType.ROOM_TYPE, 0L);
        var ratePlans = byType.getOrDefault(CodeType.RATE_PLAN, 0L);
        var pending = services.pendingMappings(i.crsHotelCode);
        i.pendingMappings = pending.size();
        i.contrastSummary = "Opera %s: %d room types, %d rate plans, %d boards, %d sources, %d payment methods. %d CRS code(s) without equivalence."
                .formatted(i.pmsHotelCode, roomTypes, ratePlans, byType.getOrDefault(CodeType.BOARD, 0L),
                        byType.getOrDefault(CodeType.CHANNEL, 0L), byType.getOrDefault(CodeType.PAYMENT_METHOD, 0L),
                        pending.size());
        i.contrastedAt = clock.instant();
        var configured = roomTypes > 0 && ratePlans > 0;
        i.propertyConfigured = configured;
        if (!configured && i.status != IntegrationStatus.PENDING_CONFIGURATION) {
            transition(i, IntegrationStatus.PENDING_CONFIGURATION, "The property is not configured in Opera: " + i.contrastSummary);
            notifyAttention(i, "Opera property " + i.pmsHotelCode + " is not configured",
                    i.contrastSummary + " Configuring the property in Opera is outside the integration (R10); the onboarding goes on once it is.");
        } else if (configured) {
            if (i.status == IntegrationStatus.PENDING_CONFIGURATION || i.status == IntegrationStatus.CREATED) {
                transition(i, IntegrationStatus.MAPPING_PENDING, "Catalogues contrasted: " + i.contrastSummary);
            }
        }
    }

    /**
     * «Importar interlocutores» — a seed, not the flow: partners go from the ERP to Opera, projected by
     * «Proyectar Interlocutor». This brings the chain's partners as Opera already has them into the
     * ERP — created, or their name and type brought up to date — so that a demo or a first load starts
     * from what exists. The ERP records which Opera profile each one is, and the mapping learns it, so
     * none is ever created in Opera again. Their codes are Opera's CorporateIds, which is what the chain
     * knows a partner by. Nothing is written to Opera. Idempotent.
     */
    @Audited("Import partners")
    @Transactional
    public Integration importPartners(String id, String by) {
        var i = find(id);
        var summary = importPartners(i, by);
        if (i.status == IntegrationStatus.SYNCING_PARTNERS) {
            i.partnersMissing = missingPartners(i);
        }
        i.record(clock.instant(), by, summary);
        integrations.save(i);
        return i;
    }

    String importPartners(Integration i, String by) {
        definePartnerTypes(by);
        int created = 0, updated = 0, unchanged = 0;
        var fromOpera = services.pmsPartners(i.pmsHotelCode);
        // A code on more than one profile is nobody in particular: which one a reservation means cannot
        // be told, so none of them is imported, and it is said — it is Opera's data to fix.
        var ambiguous = fromOpera.stream().collect(Collectors.groupingBy(io.mateu.ecdemo1.integration.model.partner.PmsPartner::code,
                        Collectors.counting()))
                .entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).sorted().toList();
        for (var p : fromOpera) {
            if (ambiguous.contains(p.code())) {
                continue;
            }
            var type = switch (p.profileType()) {
                case "Agent" -> "TravelAgent";
                case "Company" -> "Company";
                default -> "OnlineAgency";
            };
            var current = services.erpPartner(p.code());
            if (current.isEmpty()) {
                services.createPartner(p.code(), details(type, p.name(), null));
                created++;
            } else if (!type.equals(current.get().path("type").asText()) || !p.name().equals(current.get().path("name").asText())) {
                services.updatePartner(p.code(), details(type, p.name(), current.get()));
                updated++;
            } else {
                unchanged++;
            }
            services.recordErpPmsProfile(p.code(), p.pmsProfileId(), p.profileType());
            services.recordPartnerProfile(p.code(), p.pmsProfileId(), p.profileType());
        }
        return "Partners imported from Opera %s: %d new, %d updated, %d unchanged%s".formatted(i.pmsHotelCode, created, updated,
                unchanged, ambiguous.isEmpty() ? "" : "; left out, on more than one Opera profile: " + String.join(", ", ambiguous));
    }

    /**
     * Which OPERA profile type each partner type is: certain when the partners come from Opera, so the
     * integration enters it rather than leaving it pending for a person. Keyed by the integration's
     * canonical type, which is what preparing a partner resolves.
     */
    void definePartnerTypes(String by) {
        for (var type : java.util.Map.of(
                io.mateu.ecdemo1.integration.model.partner.PartnerType.TRAVEL_AGENT, "Agent",
                io.mateu.ecdemo1.integration.model.partner.PartnerType.TOUR_OPERATOR, "Agent",
                io.mateu.ecdemo1.integration.model.partner.PartnerType.COMPANY, "Company",
                io.mateu.ecdemo1.integration.model.partner.PartnerType.ONLINE_AGENCY, "Source").entrySet()) {
            services.define(io.mateu.ecdemo1.integration.model.mapping.CodeType.PARTNER_TYPE, null, type.getKey().name(), type.getValue(), by);
        }
    }

    /** The ERP's details for an imported partner: Opera's name and type, and what the ERP already knew of the rest. */
    static Map<String, Object> details(String type, String name, com.fasterxml.jackson.databind.JsonNode current) {
        var details = new java.util.HashMap<String, Object>();
        details.put("type", type);
        details.put("name", name);
        // Opera does not say who pays the stay; the guest at the desk until the ERP says otherwise.
        details.put("billingMode", current == null ? "Front" : current.path("billingMode").asText("Front"));
        if (current != null) {
            for (var field : List.of("taxId", "email", "phone")) {
                if (!current.path(field).isNull() && !current.path(field).isMissingNode()) {
                    details.put(field, current.path(field).asText());
                }
            }
            if (current.path("address").isObject()) {
                details.put("address", current.path("address"));
            }
        }
        return details;
    }

    /** The partners the hotel's future reservations reference that are not PMS profiles yet. */
    List<String> missingPartners(Integration i) {
        var missing = new ArrayList<String>();
        for (var p : services.futureUsage(i.crsHotelCode).partners()) {
            if (!services.hasPartnerProfile(p.partnerCode())) {
                missing.add(p.partnerCode());
            }
        }
        return missing;
    }

    void gaps(Integration i) {
        var usage = services.futureUsage(i.crsHotelCode);
        i.futureReservations = usage.reservations();
        i.gaps = new ArrayList<>(services.gaps(usage));
        i.gapsCheckedAt = clock.instant();
        if (i.gaps.isEmpty() && i.status == IntegrationStatus.BACKFILL_BLOCKED) {
            i.record(clock.instant(), "recheck", "Pre-pass clear: %d future reservation(s) to project".formatted(i.futureReservations));
        }
    }

    BackfillRun startBackfill(Integration i, boolean onboarding, String by) {
        var run = new BackfillRun();
        run.id = UUID.randomUUID().toString();
        run.integrationId = i.id;
        run.crsHotelCode = i.crsHotelCode;
        run.status = BackfillRun.Status.RUNNING;
        run.onboarding = onboarding;
        run.expected = i.futureReservations;
        run.windowEnd = LocalDate.now(clock).plusDays(properties.activationWindowDays());
        run.startedAt = clock.instant();
        run.startedBy = by;
        runs.save(run);
        i.availabilitySuspendedSince = clock.instant();
        if (onboarding) {
            transition(i, IntegrationStatus.BACKFILLING, "Backfill started: nearest arrival first; the property's availability is suspended meanwhile");
        } else {
            i.record(clock.instant(), by, "Backfill relaunched; the property's availability is suspended meanwhile");
        }
        return run;
    }

    void transition(Integration i, IntegrationStatus to, String what) {
        log.info("Integration {} ({}): {} -> {}", i.id, i.crsHotelCode, i.status, to);
        i.status = to;
        i.record(clock.instant(), "onboarding", what);
        if (to == IntegrationStatus.BACKFILL_BLOCKED) {
            notifyAttention(i, "The backfill of " + i.crsHotelCode + " waits on " + i.gaps.size() + " gap(s)", describeGaps(i)
                    + ". Map the codes or project the partners; the backfill starts once none is left.");
        } else if (to == IntegrationStatus.READY_TO_ACTIVATE) {
            notifyAttention(i, "Hotel " + i.crsHotelCode + " is ready to activate",
                    "The backfill has covered the next %d days. Activating switches real-time traffic on."
                            .formatted(properties.activationWindowDays()));
        }
    }

    void notifyAttention(Integration i, String title, String body) {
        outbox.appendNotification(new NotificationRequested(UUID.randomUUID().toString(),
                NotificationType.INTEGRATION_NEEDS_ATTENTION, i.crsHotelCode, "integration/" + i.crsHotelCode, title, body,
                properties.consoleUrl() + "/integrations/integrations/" + i.id,
                "integration:" + i.id + ":" + i.status + ":" + clock.millis(), clock.instant()));
    }

    static String describeGaps(Integration i) {
        return i.gaps.stream().limit(5).map(g -> "%s %s (%d reservation(s))".formatted(
                        "PARTNER".equals(g.kind()) ? "partner" : g.type(), g.code(), g.reservations()))
                .collect(Collectors.joining(", ")) + (i.gaps.size() > 5 ? ", …" : "");
    }

    /** How to reach the property, secret opened — for the connector only. */
    public OhipConnection connection(Integration i) {
        return new OhipConnection(i.pmsHotelCode, i.gatewayUrl, i.appKey, i.clientId, secrets.open(i.clientSecretSealed),
                i.enterpriseId);
    }

    public Integration find(String id) {
        return integrations.findById(id).orElseThrow(() -> new NoSuchElementException("No integration " + id));
    }

    static void require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("An integration needs " + what);
        }
    }

    static String blankOr(String value, String current) {
        return value == null || value.isBlank() ? current : value.trim();
    }
}
