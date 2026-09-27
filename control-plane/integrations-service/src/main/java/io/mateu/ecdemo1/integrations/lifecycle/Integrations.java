package io.mateu.ecdemo1.integrations.lifecycle;

import io.mateu.ecdemo1.integration.model.integration.ConnectivityCheck;
import io.mateu.ecdemo1.integration.model.integration.Gap;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.integration.PmsProperty;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.integration.model.partner.PmsPartner;
import io.mateu.ecdemo1.integration.model.process.Definitions;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.integrations.audit.Audited;
import io.mateu.ecdemo1.integrations.clients.Services;
import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import io.mateu.ecdemo1.integrations.crypto.SecretBox;
import io.mateu.ecdemo1.integrations.outbox.Outbox;
import io.mateu.ecdemo1.integrations.outbox.Commands;
import io.mateu.ecdemo1.integrations.store.BackfillRun;
import io.mateu.ecdemo1.integrations.store.BackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.Integration;
import io.mateu.ecdemo1.integrations.store.IntegrationRepository;
import io.mateu.ecdemo1.integrations.store.IntegrationTransition;
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
import java.util.function.Consumer;
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
 *
 * <p>No other service is called while a database transaction is open. Each action reads the
 * integration, asks the other services what it needs to know — outside any transaction — and then
 * saves what it decided in one short transaction ({@link Writes}), which the integration's version
 * guards: if it changed meanwhile, nothing is saved and the action is refused, to be tried again.
 * What is to be asked of other services after deciding — the mapping, the master of partners, the
 * CRS adapter — is a command written to the outbox in that same transaction ({@link Commands}) and
 * published to Kafka once it commits: a decision rolled back asks nothing of anyone. Other services
 * are called over HTTP only to be asked something the decision needs now — queries. The integration's status changes only through its
 * {@link IntegrationTransition state machine}.
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

    /** What an action decided, applied to the integration inside the transaction that saves it. */
    interface Change extends Consumer<Integration> {
        Change NONE = i -> {
        };

        default Change then(Consumer<Integration> next) {
            return i -> {
                accept(i);
                next.accept(i);
            };
        }
    }

    final IntegrationRepository integrations;
    final BackfillRunRepository runs;
    final Services services;
    final SecretBox secrets;
    final Outbox outbox;
    final Commands commands;
    final Writes writes;
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
    public Integration register(Registration r, String by) {
        var chain = chainConnection();
        var registration = new Registration(r.crsHotelCode(), r.pmsHotelCode(), r.name(), blankOr(r.gatewayUrl(), chain.gatewayUrl()),
                blankOr(r.appKey(), chain.appKey()), blankOr(r.clientId(), chain.clientId()),
                blankOr(r.clientSecret(), chain.clientSecret()), blankOr(r.enterpriseId(), chain.enterpriseId()));
        return writes.write(() -> registerFilled(registration, by));
    }

    Integration registerFilled(Registration r, String by) {
        require(r.crsHotelCode(), "the CRS hotel");
        require(r.pmsHotelCode(), "the Opera property");
        require(r.gatewayUrl(), "the OHIP gateway");
        require(r.clientSecret(), "the client secret");
        integrations.findByCrsHotelCode(r.crsHotelCode()).ifPresent(existing -> {
            throw new IllegalStateException("Hotel %s already has an integration (%s, %s)"
                    .formatted(r.crsHotelCode(), existing.id, existing.getStatus()));
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
        i.begin();
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
    public Integration changeConnection(String id, ConnectionChange c, String by) {
        var i = find(id);
        // Changed on the integration as read, not saved yet: the connection is tried with it first.
        i.gatewayUrl = blankOr(c.gatewayUrl(), i.gatewayUrl);
        i.appKey = blankOr(c.appKey(), i.appKey);
        i.clientId = blankOr(c.clientId(), i.clientId);
        i.enterpriseId = blankOr(c.enterpriseId(), i.enterpriseId);
        if (c.clientSecret() != null && !c.clientSecret().isBlank()) {
            i.clientSecretSealed = secrets.seal(c.clientSecret());
        }
        i.record(clock.instant(), by, "Connection changed" + (c.clientSecret() == null || c.clientSecret().isBlank()
                ? "" : " (new secret)"));
        return save(i, verification(i, by));
    }

    /** Tries the connection again, now. */
    @Audited("Verify connection")
    public Integration verifyNow(String id, String by) {
        var i = find(id);
        return save(i, verification(i, by));
    }

    /** Looks again at whatever the current gate needs from outside: the catalogue, the partners, the gaps. */
    @Audited("Recheck")
    public Integration recheck(String id, String by) {
        var i = find(id);
        return save(i, recheck(i));
    }

    /**
     * The mapping gate, opened by hand: a person says the hotel's codes are mapped well enough to go
     * on, some still pending. The backfill's pre-pass insists on the ones the reservations really use.
     */
    @Audited("Approve mapping")
    public Integration approveMapping(String id, String by) {
        return writes.write(() -> {
            var i = find(id);
            if (!i.is(IntegrationStatus.MAPPING_PENDING)) {
                throw new IllegalStateException("The mapping is approved while the integration waits for it; it is " + i.getStatus());
            }
            i.mappingApprovedAt = clock.instant();
            i.mappingApprovedBy = by;
            i.record(clock.instant(), by, "Mapping approved (%s code(s) still without equivalence)".formatted(i.pendingMappings));
            return integrations.save(i);
        });
    }

    /** The activation gate: a person switches real-time traffic on. */
    @Audited("Activate integration")
    public Integration activate(String id, String by) {
        return writes.write(() -> {
            var i = find(id);
            if (!IntegrationTransition.ACTIVATE.allowedFrom(i.getStatus())) {
                throw new IllegalStateException("Only an integration ready to activate can be activated; it is " + i.getStatus());
            }
            i.activationRequestedAt = clock.instant();
            i.activationRequestedBy = by;
            i.record(clock.instant(), by, "Activation requested");
            return integrations.save(i);
        });
    }

    /** Real-time traffic of the hotel waits — every process on one cause — until it is resumed. */
    @Audited("Pause integration")
    public Integration pause(String id, String by) {
        return writes.write(() -> {
            var i = find(id);
            i.apply(IntegrationTransition.PAUSE);
            i.pausedAt = clock.instant();
            i.pausedBy = by;
            i.record(clock.instant(), by, "Paused");
            return integrations.save(i);
        });
    }

    /** Real-time traffic flows again, starting with what waited while the integration was paused. */
    @Audited("Resume integration")
    public Integration resume(String id, String by) {
        return writes.write(() -> {
            var i = find(id);
            i.apply(IntegrationTransition.RESUME);
            i.record(clock.instant(), by, "Resumed");
            commands.resolveCauseIfOpen(Cause.integrationInactive(i.crsHotelCode).key(), by);
            return integrations.save(i);
        });
    }

    /**
     * Takes the integration down. What reached the PMS stays there — deciding about it is the
     * migration's rollback strategy (R5), not this. A half-done onboarding stays where it was: its
     * gates never open again. One already decommissioned is not decommissioned again.
     */
    @Audited("Decommission integration")
    public Integration decommission(String id, String by) {
        return writes.write(() -> {
            var i = find(id);
            i.apply(IntegrationTransition.DECOMMISSION);
            i.gate = null;
            i.decommissionedAt = clock.instant();
            i.decommissionedBy = by;
            runs.findByStatus(BackfillRun.Status.RUNNING).stream().filter(r -> r.integrationId.equals(i.id)).forEach(r -> {
                r.status = BackfillRun.Status.STOPPED;
                r.finishedAt = clock.instant();
                runs.save(r);
            });
            i.record(clock.instant(), by, "Decommissioned");
            // Whatever it was waiting for a person to do, nobody has to any more.
            outbox.appendResolution("integration/" + i.crsHotelCode, by);
            return integrations.save(i);
        });
    }

    /** A backfill on demand (F013): after a long stop, or to recover a gap. Real-time traffic goes on meanwhile. */
    @Audited("Relaunch backfill")
    public BackfillRun relaunchBackfill(String id, String by) {
        return writes.write(() -> {
            var i = find(id);
            if (!i.is(IntegrationStatus.ACTIVE) && !i.is(IntegrationStatus.PAUSED)) {
                throw new IllegalStateException("A backfill is relaunched on a running integration; this one is " + i.getStatus()
                        + " — its onboarding runs its own");
            }
            var run = startBackfill(i, false, by);
            integrations.save(i);
            return run;
        });
    }

    // ── the onboarding's steps ──────────────────────────────────────────────
    //
    // A step on a decommissioned integration does nothing: its onboarding was abandoned, and a step
    // run late must not bring its gates back.

    /** «Registrar y verificar conectividad». */
    public void stepVerifyConnectivity(String id) {
        var i = find(id);
        if (abandoned(i, "verify-connectivity")) {
            return;
        }
        save(i, verification(i, "onboarding").then(x -> x.gate = Definitions.GATE_CONNECTIVITY));
    }

    /** «Verificar el estado inicial»: the property's catalogue against the CRS's. */
    public void stepContrastCatalogues(String id) {
        var i = find(id);
        if (abandoned(i, "contrast-catalogues")) {
            return;
        }
        save(i, contrast(i).then(x -> x.gate = Definitions.GATE_CONFIGURED));
    }

    /**
     * «Mapeado»: asks the mapping agent for a proposal of whatever is pending — or, with
     * {@code integrations.mapping.ask-agent} off, leaves a person a notice to ask it — and waits. The gate
     * opens when a person has approved every equivalence the hotel needs — or when a person says to
     * go on regardless ({@link #approveMapping}).
     */
    public void stepRequestMapping(String id) {
        var i = find(id);
        if (abandoned(i, "request-mapping")) {
            return;
        }
        var pending = services.pendingMappings(i.crsHotelCode).size();
        save(i, x -> {
            x.pendingMappings = pending;
            if (!x.is(IntegrationStatus.MAPPING_PENDING)) {
                transition(x, IntegrationTransition.AWAIT_MAPPING, "Waiting for the mapping: %d code(s) pending".formatted(pending));
            }
            if (pending > 0 && !properties.mapping().askAgent()) {
                // A person asks the agent: the notice says what is missing and where to map it; it closes
                // on its own when the integration leaves the gate (transition() resolves it).
                notifyAttention(x, "Hotel %s has %d code(s) without an equivalent in the PMS".formatted(x.crsHotelCode, pending),
                        "Map them in Mapping → Dictionary, choosing the integration — or ask the mapping agent for a proposal there"
                                + " and approve it.",
                        properties.consoleUrl() + "/mapping/dictionary?integration=" + x.crsHotelCode);
                x.record(clock.instant(), "onboarding", "Waiting for a person to map the %d pending code(s)".formatted(pending));
            } else if (pending > 0) {
                // Published once this is saved; the mapping takes it once.
                commands.requestAgentProposal(x.crsHotelCode);
                x.record(clock.instant(), "onboarding", "Asked the mapping agent for a proposal of the %d pending code(s)"
                        .formatted(pending));
            }
            x.gate = Definitions.GATE_MAPPING;
        });
    }

    /**
     * «Sincronizar interlocutores»: the partners the hotel's future reservations reference (R12: the
     * subset the property needs, not the whole master) that are not yet profiles in the PMS are
     * announced again, and projected by «Proyectar Interlocutor». Before the backfill, or it fails in
     * cascade.
     */
    public void stepSyncPartners(String id) {
        var i = find(id);
        if (abandoned(i, "sync-partners")) {
            return;
        }
        var missing = missingPartners(i);
        save(i, x -> {
            if (!x.is(IntegrationStatus.SYNCING_PARTNERS)) {
                transition(x, IntegrationTransition.SYNC_PARTNERS, "Syncing the partners of the hotel's future reservations");
            }
            missing.forEach(commands::resyncPartner);
            x.partnersMissing = missing;
            x.record(clock.instant(), "onboarding", missing.isEmpty() ? "Every partner is already a PMS profile"
                    : "Announced again for projection: " + String.join(", ", missing));
            x.gate = Definitions.GATE_PARTNERS;
        });
    }

    /** The backfill's pre-pass: what its reservations really use and still lack. A gate if anything. */
    public void stepBackfillPrePass(String id) {
        var i = find(id);
        if (abandoned(i, "backfill-prepass")) {
            return;
        }
        save(i, gaps(i).then(x -> {
            if (!x.gaps.isEmpty()) {
                if (!x.is(IntegrationStatus.BACKFILL_BLOCKED)) {
                    transition(x, IntegrationTransition.BLOCK_BACKFILL, "The backfill waits: %d gap(s) — %s"
                            .formatted(x.gaps.size(), describeGaps(x)));
                }
            } else if (!x.is(IntegrationStatus.BACKFILL_BLOCKED)) {
                x.record(clock.instant(), "onboarding", "Pre-pass clear: %d future reservation(s) to project".formatted(x.futureReservations));
            }
            x.gate = Definitions.GATE_BACKFILL_CLEAR;
        }));
    }

    /** «Backfill»: suspends the property's availability and starts projecting, nearest arrival first. */
    public void stepStartBackfill(String id) {
        writes.write(() -> {
            var i = find(id);
            if (abandoned(i, "start-backfill")) {
                return i;
            }
            var existing = runs.findFirstByIntegrationIdOrderByStartedAtDesc(i.id)
                    .filter(r -> r.onboarding && r.status != BackfillRun.Status.STOPPED);
            if (existing.isEmpty()) {
                startBackfill(i, true, "onboarding");
            }
            i.gate = Definitions.GATE_WINDOW;
            return integrations.save(i);
        });
    }

    /** The window is covered: the hotel can be activated, when a person says so. */
    public void stepAwaitActivation(String id) {
        writes.write(() -> {
            var i = find(id);
            if (abandoned(i, "await-activation")) {
                return i;
            }
            if (i.is(IntegrationStatus.BACKFILLING)) {
                transition(i, IntegrationTransition.WINDOW_COVERED, "Ready to activate: the next %d days are in the PMS"
                        .formatted(properties.activationWindowDays()));
            }
            i.gate = Definitions.GATE_ACTIVATION;
            return integrations.save(i);
        });
    }

    /** «Activar»: real-time traffic flows, starting with what waited for this. */
    public void stepActivate(String id) {
        writes.write(() -> {
            var i = find(id);
            if (abandoned(i, "activate")) {
                return i;
            }
            if (i.is(IntegrationStatus.READY_TO_ACTIVATE)) {
                transition(i, IntegrationTransition.ACTIVATE, i.activationRequestedBy, "Active: real-time traffic flows");
                i.activatedAt = clock.instant();
            }
            i.gate = null;
            if (i.is(IntegrationStatus.ACTIVE)) {
                commands.resolveCauseIfOpen(Cause.integrationInactive(i.crsHotelCode).key(), "integration " + i.crsHotelCode);
            }
            return integrations.save(i);
        });
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

    /**
     * Looks again at what a gate needs from outside — asking now, outside any transaction — and
     * returns what to record of it. Nothing for the gates a person opens.
     */
    Change recheck(Integration i) {
        return switch (i.getStatus()) {
            case CONNECTIVITY_FAILED -> verification(i, "recheck");
            case PENDING_CONFIGURATION -> contrast(i);
            case MAPPING_PENDING -> {
                var pending = services.pendingMappings(i.crsHotelCode).size();
                yield x -> {
                    var before = x.pendingMappings;
                    x.pendingMappings = pending;
                    if (pending == 0 && (before == null || before > 0)) {
                        x.record(clock.instant(), "recheck", "Every code of the hotel has an approved equivalence");
                    }
                };
            }
            case SYNCING_PARTNERS -> {
                var missing = missingPartners(i);
                yield x -> x.partnersMissing = missing;
            }
            case BACKFILL_BLOCKED -> gaps(i);
            default -> Change.NONE;
        };
    }

    /** What {@link Gates} looks at on its own, every so often. */
    public void recheckAutomatically(String id) {
        var i = find(id);
        save(i, recheck(i));
    }

    /** Sends the message that opens the integration's gate: the process moves on, if it is waiting there. */
    @Transactional
    public void signalGate(String id) {
        var i = find(id);
        if (gateOpen(i)) {
            outbox.appendToEngine(new MessageReceived(i.gate, i.processKey, List.of()));
        }
    }

    // ── the work behind the steps: ask outside, then decide ─────────────────

    /** Saves what was decided on the integration as it was read: refused if it has changed since. */
    Integration save(Integration i, Consumer<Integration> change) {
        return writes.write(() -> {
            change.accept(i);
            return integrations.save(i);
        });
    }

    /** Tries the connection; what it answered, to record. */
    Change verification(Integration i, String by) {
        var check = services.verify(connection(i));
        return x -> verified(x, check, by);
    }

    void verified(Integration i, ConnectivityCheck check, String by) {
        i.connectivityOk = check.ok();
        i.connectivityMessage = check.message();
        i.connectivityCheckedAt = clock.instant();
        if (check.ok()) {
            commands.defineHotel(i.crsHotelCode, i.pmsHotelCode, "integration " + i.crsHotelCode);
            // Which profile type each partner type is in OPERA: the tenant's own types, so entered
            // rather than left pending for a person — creating a partner's profile needs it.
            commands.definePartnerTypes("integration " + i.crsHotelCode);
            if (i.is(IntegrationStatus.CONNECTIVITY_FAILED)) {
                transition(i, IntegrationTransition.CONNECTIVITY_RESTORED, "Connection verified: " + check.message());
            } else {
                i.record(clock.instant(), by, "Connection verified: " + check.message());
            }
        } else if (i.is(IntegrationStatus.CREATED)) {
            transition(i, IntegrationTransition.CONNECTIVITY_FAILS, "Opera does not take the connection: " + check.message());
            notifyAttention(i, "Opera does not take the connection of " + i.crsHotelCode,
                    check.message() + ". Fix the connection data or the credentials; the onboarding goes on once they work.");
        }
    }

    /** The property's catalogue against what the CRS still lacks; what it says, to record. */
    Change contrast(Integration i) {
        List<CodeEntry> catalog = services.pmsCatalog(i.pmsHotelCode);
        var pending = services.pendingMappings(i.crsHotelCode).size();
        return x -> contrasted(x, catalog, pending);
    }

    void contrasted(Integration i, List<CodeEntry> catalog, int pending) {
        var byType = catalog.stream().filter(e -> i.pmsHotelCode.equals(e.hotelCode()))
                .collect(Collectors.groupingBy(CodeEntry::type, Collectors.counting()));
        var roomTypes = byType.getOrDefault(CodeType.ROOM_TYPE, 0L);
        var ratePlans = byType.getOrDefault(CodeType.RATE_PLAN, 0L);
        i.pendingMappings = pending;
        i.contrastSummary = "Opera %s: %d room types, %d rate plans, %d boards, %d sources, %d payment methods. %d CRS code(s) without equivalence."
                .formatted(i.pmsHotelCode, roomTypes, ratePlans, byType.getOrDefault(CodeType.BOARD, 0L),
                        byType.getOrDefault(CodeType.CHANNEL, 0L), byType.getOrDefault(CodeType.PAYMENT_METHOD, 0L),
                        pending);
        i.contrastedAt = clock.instant();
        var configured = roomTypes > 0 && ratePlans > 0;
        i.propertyConfigured = configured;
        if (!configured && !i.is(IntegrationStatus.PENDING_CONFIGURATION)) {
            transition(i, IntegrationTransition.PROPERTY_UNCONFIGURED, "The property is not configured in Opera: " + i.contrastSummary);
            notifyAttention(i, "Opera property " + i.pmsHotelCode + " is not configured",
                    i.contrastSummary + " Configuring the property in Opera is outside the integration (R10); the onboarding goes on once it is.");
        } else if (configured && IntegrationTransition.AWAIT_MAPPING.allowedFrom(i.getStatus())) {
            transition(i, IntegrationTransition.AWAIT_MAPPING, "Catalogues contrasted: " + i.contrastSummary);
        }
    }

    /**
     * «Importar interlocutores» — a seed, not the flow: partners go from the ERP to Opera, projected by
     * «Proyectar Interlocutor». This brings the chain's partners as Opera already has them into the
     * ERP — created, or their name and type brought up to date — so that a demo or a first load starts
     * from what exists. The ERP records which Opera profile each one is, and the mapping learns it, so
     * none is ever created in Opera again. Their codes are Opera's CorporateIds, which is what the chain
     * knows a partner by. Nothing is written to Opera. Idempotent.
     *
     * <p>Opera's partners and the ERP's are read first, outside any transaction — to tell what is new
     * and what changes, which is what the person is told. What to do about them goes as commands to the
     * ERP and the mapping, written in the transaction that records the import: the ERP creates or
     * updates each one — keeping what only it knows — and the gates see the profiles once they are in.
     */
    @Audited("Import partners")
    public Integration importPartners(String id, String by) {
        var i = find(id);
        var plan = planImport(i);
        return writes.write(() -> {
            var now = find(id);
            commands.definePartnerTypes(by);
            for (var p : plan.partners()) {
                commands.importPartner(p.code(), erpType(p.profileType()), p.name(), p.pmsProfileId(), p.profileType());
                commands.recordPartnerProfile(p.code(), p.pmsProfileId(), p.profileType());
            }
            now.record(clock.instant(), by, plan.summary(i.pmsHotelCode));
            return integrations.save(now);
        });
    }

    /** What an import brings: Opera's partners but the ambiguous ones, and how many are new or change in the ERP. */
    record ImportPlan(List<PmsPartner> partners, int created, int updated, int unchanged, List<String> ambiguous) {
        String summary(String pmsHotelCode) {
            return "Partners imported from Opera %s: %d new, %d updated, %d unchanged%s".formatted(pmsHotelCode, created, updated,
                    unchanged, ambiguous.isEmpty() ? "" : "; left out, on more than one Opera profile: " + String.join(", ", ambiguous));
        }
    }

    ImportPlan planImport(Integration i) {
        int created = 0, updated = 0, unchanged = 0;
        var fromOpera = services.pmsPartners(i.pmsHotelCode);
        // A code on more than one profile is nobody in particular: which one a reservation means cannot
        // be told, so none of them is imported, and it is said — it is Opera's data to fix.
        var ambiguous = fromOpera.stream().collect(Collectors.groupingBy(PmsPartner::code, Collectors.counting()))
                .entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).sorted().toList();
        var partners = new ArrayList<PmsPartner>();
        for (var p : fromOpera) {
            if (ambiguous.contains(p.code())) {
                continue;
            }
            partners.add(p);
            var type = erpType(p.profileType());
            var current = services.erpPartner(p.code());
            if (current.isEmpty()) {
                created++;
            } else if (!type.equals(current.get().path("type").asText()) || !p.name().equals(current.get().path("name").asText())) {
                updated++;
            } else {
                unchanged++;
            }
        }
        return new ImportPlan(partners, created, updated, unchanged, ambiguous);
    }

    /** The ERP's partner type for an Opera profile type. */
    static String erpType(String profileType) {
        return switch (profileType) {
            case "Agent" -> "TravelAgent";
            case "Company" -> "Company";
            default -> "OnlineAgency";
        };
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

    /** What the hotel's future reservations use and the mapping still lacks; to record. */
    Change gaps(Integration i) {
        var usage = services.futureUsage(i.crsHotelCode);
        List<Gap> found = new ArrayList<>(services.gaps(usage));
        return x -> {
            x.futureReservations = usage.reservations();
            x.gaps = new ArrayList<>(found);
            x.gapsCheckedAt = clock.instant();
            if (x.gaps.isEmpty() && x.is(IntegrationStatus.BACKFILL_BLOCKED)) {
                x.record(clock.instant(), "recheck", "Pre-pass clear: %d future reservation(s) to project".formatted(x.futureReservations));
            }
        };
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
        if (onboarding) {
            transition(i, IntegrationTransition.START_BACKFILL,
                    "Backfill started: nearest arrival first; the property's availability is suspended meanwhile");
        } else {
            i.record(clock.instant(), by, "Backfill relaunched; the property's availability is suspended meanwhile");
        }
        runs.save(run);
        i.availabilitySuspendedSince = clock.instant();
        return run;
    }

    void transition(Integration i, IntegrationTransition t, String what) {
        transition(i, t, "onboarding", what);
    }

    /** The onboarding moves the integration on: recorded, and whatever it waited for at its last gate is done. */
    void transition(Integration i, IntegrationTransition t, String by, String what) {
        var from = i.getStatus();
        var to = i.apply(t);
        log.info("Integration {} ({}): {} -> {}", i.id, i.crsHotelCode, from, to);
        i.record(clock.instant(), by, what);
        // Whatever the integration was waiting for at its last gate, it is past it: that notification is done.
        outbox.appendResolution("integration/" + i.crsHotelCode, "onboarding");
        if (to == IntegrationStatus.BACKFILL_BLOCKED) {
            notifyAttention(i, "The backfill of " + i.crsHotelCode + " waits on " + i.gaps.size() + " gap(s)", describeGaps(i)
                    + ". Map the codes or project the partners; the backfill starts once none is left.");
        } else if (to == IntegrationStatus.READY_TO_ACTIVATE) {
            notifyAttention(i, "Hotel " + i.crsHotelCode + " is ready to activate",
                    "The backfill has covered the next %d days. Activating switches real-time traffic on."
                            .formatted(properties.activationWindowDays()));
        }
    }

    boolean abandoned(Integration i, String step) {
        if (i.is(IntegrationStatus.DECOMMISSIONED)) {
            log.info("Step {} of integration {} ({}) skipped: it is decommissioned", step, i.id, i.crsHotelCode);
            return true;
        }
        return false;
    }

    void notifyAttention(Integration i, String title, String body) {
        notifyAttention(i, title, body, properties.consoleUrl() + "/integrations/integrations/" + i.id);
    }

    void notifyAttention(Integration i, String title, String body, String link) {
        outbox.appendNotification(new NotificationRequested(UUID.randomUUID().toString(),
                NotificationType.INTEGRATION_NEEDS_ATTENTION, i.crsHotelCode, "integration/" + i.crsHotelCode, title, body,
                link,
                "integration:" + i.id + ":" + i.getStatus() + ":" + clock.millis(), clock.instant()));
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
