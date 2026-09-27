package io.mateu.ecdemo1.integrations.frontoffice;

import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCatalogueSummary;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand;
import io.mateu.ecdemo1.integration.model.integration.ConnectivityCheck;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.integration.model.pms.PmsReservationStamp;
import io.mateu.ecdemo1.integration.model.process.Definitions;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.integrations.audit.Audited;
import io.mateu.ecdemo1.integrations.clients.Services;
import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import io.mateu.ecdemo1.integrations.lifecycle.Integrations;
import io.mateu.ecdemo1.integrations.lifecycle.Writes;
import io.mateu.ecdemo1.integrations.outbox.Outbox;
import io.mateu.ecdemo1.integrations.store.FoBackfillRun;
import io.mateu.ecdemo1.integrations.store.FoBackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.FoIntegrationStatus;
import io.mateu.ecdemo1.integrations.store.FoIntegrationTransition;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegration;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegrationRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The pms-fo integration's behaviour: a hotel's front office fed from its PMS — what a person does to
 * it, and what each step of its onboarding does («alta-integracion-fo»). The same machinery as the
 * crs-pms onboarding ({@link Integrations}): each step does its work, records it, and names the gate
 * the process waits at next; {@link FoGates} sends the message that opens it once what it waits for
 * is recorded.
 *
 * <p>The onboarding is shorter: both ends answer (the PMS readable, the front office reachable); the
 * PMS's catalogue — room types, rate plans, packages, rooms — reaches the front office, so it reads
 * the stays in the PMS's words; the backfill projects the property's reservations of the horizon; a
 * person activates it, and from then on every change reaches the front office — as the connector
 * writes the PMS ({@code pms-reservations}) and, for what was changed in the PMS itself, by polling it
 * ({@link FoPolling}).
 *
 * <p>No other service is called while a transaction is open: what a decision needs is asked first,
 * outside any transaction; what it asks of others — the catalogue to the front office, the processes
 * of the engine — goes through the outbox in the transaction that saves it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FrontOfficeIntegrations {

    public record Registration(String pmsHotelCode, String frontOfficeCode, String name, String frontOfficeUrl,
                               FrontOfficeIntegration.Scope scope, Integer horizonDays) {
    }

    /** What an action decided, applied to the integration inside the transaction that saves it. */
    interface Change extends Consumer<FrontOfficeIntegration> {
        Change NONE = i -> {
        };

        default Change then(Consumer<FrontOfficeIntegration> next) {
            return i -> {
                accept(i);
                next.accept(i);
            };
        }
    }

    final FrontOfficeIntegrationRepository integrations;
    final FoBackfillRunRepository runs;
    final Services services;
    final Integrations crsPms;
    final Outbox outbox;
    final StayProjections projections;
    final Writes writes;
    final IntegrationsProperties properties;
    final Clock clock;

    // ── what a person does ──────────────────────────────────────────────────

    /** Registers the integration and starts its onboarding. One per PMS property. */
    @Audited("Register front office integration")
    public FrontOfficeIntegration register(Registration r, String by) {
        return writes.write(() -> {
            require(r.pmsHotelCode(), "the PMS property");
            require(r.frontOfficeCode(), "the front office");
            integrations.findFirstByPmsHotelCodeAndStatusNot(r.pmsHotelCode().trim(), FoIntegrationStatus.DECOMMISSIONED)
                    .ifPresent(existing -> {
                        throw new IllegalStateException("Property %s already feeds a front office (%s, %s)"
                                .formatted(existing.pmsHotelCode, existing.frontOfficeCode, existing.getStatus()));
                    });
            var i = new FrontOfficeIntegration();
            i.id = UUID.randomUUID().toString();
            i.pmsHotelCode = r.pmsHotelCode().trim();
            i.frontOfficeCode = r.frontOfficeCode().trim();
            i.name = r.name();
            i.frontOfficeUrl = blankOr(r.frontOfficeUrl(), properties.frontOfficeUrl());
            i.scope = r.scope() == null ? FrontOfficeIntegration.Scope.DEFAULT : r.scope();
            i.horizonDays = r.horizonDays() == null || r.horizonDays() <= 0 ? properties.frontOffice().horizonDays()
                    : r.horizonDays();
            i.begin();
            i.processKey = Definitions.ONBOARD_FO_INTEGRATION + ":" + i.id;
            i.createdAt = clock.instant();
            i.createdBy = by;
            i.record(clock.instant(), by, "Registered: Opera %s → front office %s (%s reservations, %d days ahead)"
                    .formatted(i.pmsHotelCode, i.frontOfficeCode, i.scope, i.horizonDays));
            integrations.save(i);
            outbox.appendToEngine(new ProcessCreationRequested(Definitions.ONBOARD_FO_INTEGRATION, i.processKey, List.of(
                    new Variable(ProcessVariables.PROCESS_KEY, i.processKey),
                    new Variable(ProcessVariables.INTEGRATION_ID, i.id),
                    new Variable(ProcessVariables.PMS_HOTEL_CODE, i.pmsHotelCode)), null, AuthorizationContext.SYSTEM));
            log.info("pms-fo integration {} registered for {} → {}: onboarding {}", i.id, i.pmsHotelCode, i.frontOfficeCode,
                    i.processKey);
            return i;
        });
    }

    /** Tries both ends again, now. */
    @Audited("Verify front office integration")
    public FrontOfficeIntegration verifyNow(String id, String by) {
        var i = find(id);
        return save(i, verification(i, by));
    }

    /** Looks again at whatever the current gate needs from outside: the connections, the catalogue. */
    @Audited("Recheck front office integration")
    public FrontOfficeIntegration recheck(String id, String by) {
        var i = find(id);
        return save(i, recheck(i));
    }

    /** The PMS's catalogue, read again and sent to the front office — after a change in the PMS's configuration. */
    @Audited("Resync front office catalogue")
    public FrontOfficeIntegration resyncCatalogue(String id, String by) {
        var i = find(id);
        if (!i.is(FoIntegrationStatus.ACTIVE) && !i.is(FoIntegrationStatus.PAUSED) && !i.is(FoIntegrationStatus.SYNCING_CATALOGUE)) {
            throw new IllegalStateException("The catalogue is synced again on a running integration or while it waits for it; it is "
                    + i.getStatus());
        }
        var entries = services.pmsFrontOfficeCatalogue(i.pmsHotelCode);
        return save(i, x -> sendCatalogue(x, entries, by));
    }

    /** The activation gate: a person switches the changes from the PMS on. */
    @Audited("Activate front office integration")
    public FrontOfficeIntegration activate(String id, String by) {
        return writes.write(() -> {
            var i = find(id);
            if (!FoIntegrationTransition.ACTIVATE.allowedFrom(i.getStatus())) {
                throw new IllegalStateException("Only a pms-fo integration ready to activate can be activated; it is " + i.getStatus());
            }
            i.activationRequestedAt = clock.instant();
            i.activationRequestedBy = by;
            i.record(clock.instant(), by, "Activation requested");
            return integrations.save(i);
        });
    }

    /** Nothing reaches the front office until it is resumed; the polling picks up from where it stopped. */
    @Audited("Pause front office integration")
    public FrontOfficeIntegration pause(String id, String by) {
        return writes.write(() -> {
            var i = find(id);
            i.apply(FoIntegrationTransition.PAUSE);
            i.pausedAt = clock.instant();
            i.pausedBy = by;
            i.record(clock.instant(), by, "Paused");
            return integrations.save(i);
        });
    }

    @Audited("Resume front office integration")
    public FrontOfficeIntegration resume(String id, String by) {
        return writes.write(() -> {
            var i = find(id);
            i.apply(FoIntegrationTransition.RESUME);
            i.record(clock.instant(), by, "Resumed: the next poll brings what changed meanwhile");
            return integrations.save(i);
        });
    }

    /** Takes it down: what reached the front office stays there. Its running backfill stops. */
    @Audited("Decommission front office integration")
    public FrontOfficeIntegration decommission(String id, String by) {
        return writes.write(() -> {
            var i = find(id);
            i.apply(FoIntegrationTransition.DECOMMISSION);
            i.gate = null;
            i.decommissionedAt = clock.instant();
            i.decommissionedBy = by;
            runs.findByStatus(FoBackfillRun.Status.RUNNING).stream().filter(r -> r.integrationId.equals(i.id)).forEach(r -> {
                r.status = FoBackfillRun.Status.STOPPED;
                r.finishedAt = clock.instant();
                runs.save(r);
            });
            i.record(clock.instant(), by, "Decommissioned");
            outbox.appendResolution(subject(i), by);
            return integrations.save(i);
        });
    }

    /** The property's reservations of the horizon, projected again: after a long pause, or to recover. */
    @Audited("Relaunch front office backfill")
    public FoBackfillRun relaunchBackfill(String id, String by) {
        var i = find(id);
        if (!i.is(FoIntegrationStatus.ACTIVE) && !i.is(FoIntegrationStatus.PAUSED)) {
            throw new IllegalStateException("A backfill is relaunched on a running integration; this one is " + i.getStatus()
                    + " — its onboarding runs its own");
        }
        var stamps = window(i, null);
        return writes.write(() -> {
            var now = find(id);
            var run = newRun(now, stamps, false, by);
            now.record(clock.instant(), by, "Backfill relaunched: %d reservation(s) to project".formatted(run.expected));
            integrations.save(now);
            return run;
        });
    }

    // ── the onboarding's steps ──────────────────────────────────────────────

    /** Both ends answer: the PMS is readable with the chain's connection, the front office is reachable. */
    public void stepVerifyConnectivity(String id) {
        var i = find(id);
        if (abandoned(i, "fo-verify-connectivity")) {
            return;
        }
        save(i, verification(i, "onboarding").then(x -> x.gate = Definitions.GATE_FO_CONNECTIVITY));
    }

    /** The PMS's catalogue, to the front office; the gate opens when the front office says it holds it. */
    public void stepSyncCatalogue(String id) {
        var i = find(id);
        if (abandoned(i, "fo-sync-catalogue")) {
            return;
        }
        if (i.catalogueCommandId != null && i.is(FoIntegrationStatus.SYNCING_CATALOGUE)) {
            // Run again by the engine: the catalogue is already on its way.
            save(i, x -> x.gate = Definitions.GATE_FO_CATALOGUE);
            return;
        }
        var entries = services.pmsFrontOfficeCatalogue(i.pmsHotelCode);
        save(i, x -> {
            if (FoIntegrationTransition.SYNC_CATALOGUE.allowedFrom(x.getStatus())) {
                transition(x, FoIntegrationTransition.SYNC_CATALOGUE, "Sending the PMS's catalogue to the front office");
            }
            sendCatalogue(x, entries, "onboarding");
            x.gate = Definitions.GATE_FO_CATALOGUE;
        });
    }

    /** «Backfill»: the property's reservations of the horizon, read once, projected a batch per tick. */
    public void stepStartBackfill(String id) {
        var i = find(id);
        if (abandoned(i, "fo-start-backfill")) {
            return;
        }
        var existing = runs.findFirstByIntegrationIdOrderByStartedAtDesc(i.id)
                .filter(r -> r.onboarding && r.status != FoBackfillRun.Status.STOPPED);
        List<PmsReservationStamp> stamps = existing.isPresent() ? List.of() : window(i, null);
        writes.write(() -> {
            var x = find(id);
            if (existing.isEmpty()) {
                var run = newRun(x, stamps, true, "onboarding");
                if (FoIntegrationTransition.START_BACKFILL.allowedFrom(x.getStatus())) {
                    transition(x, FoIntegrationTransition.START_BACKFILL,
                            "Backfill started: %d reservation(s) of the next %d days, nearest arrival first"
                                    .formatted(run.expected, x.horizonDays));
                }
            }
            x.gate = Definitions.GATE_FO_BACKFILL;
            return integrations.save(x);
        });
    }

    /** The backfill is done: the front office holds the property's reservations; a person activates it. */
    public void stepAwaitActivation(String id) {
        writes.write(() -> {
            var i = find(id);
            if (abandoned(i, "fo-await-activation")) {
                return i;
            }
            if (i.is(FoIntegrationStatus.BACKFILLING)) {
                transition(i, FoIntegrationTransition.BACKFILL_DONE,
                        "Ready to activate: the front office holds the property's reservations of the next %d days"
                                .formatted(i.horizonDays));
            }
            i.gate = Definitions.GATE_FO_ACTIVATION;
            return integrations.save(i);
        });
    }

    /** «Activar»: every change of the PMS reaches the front office, polled from where the backfill read. */
    public void stepActivate(String id) {
        writes.write(() -> {
            var i = find(id);
            if (abandoned(i, "fo-activate")) {
                return i;
            }
            if (i.is(FoIntegrationStatus.READY_TO_ACTIVATE)) {
                transition(i, FoIntegrationTransition.ACTIVATE, i.activationRequestedBy,
                        "Active: every change of the PMS reaches the front office");
                i.activatedAt = clock.instant();
            }
            if (i.pollCursor == null) {
                i.pollCursor = runs.findFirstByIntegrationIdOrderByStartedAtDesc(i.id).map(r -> r.cursorAtStart).orElse(null);
            }
            i.gate = null;
            return integrations.save(i);
        });
    }

    // ── gates ───────────────────────────────────────────────────────────────

    public boolean gateOpen(FrontOfficeIntegration i) {
        if (i.gate == null) {
            return false;
        }
        return switch (i.gate) {
            case Definitions.GATE_FO_CONNECTIVITY -> Boolean.TRUE.equals(i.connectivityOk);
            case Definitions.GATE_FO_CATALOGUE -> i.catalogueCommandId != null && i.catalogueSyncedAt != null;
            case Definitions.GATE_FO_BACKFILL -> runs.findFirstByIntegrationIdOrderByStartedAtDesc(i.id)
                    .map(r -> r.onboarding && r.status == FoBackfillRun.Status.COMPLETED).orElse(false);
            case Definitions.GATE_FO_ACTIVATION -> i.activationRequestedAt != null;
            default -> false;
        };
    }

    /** Whether {@link FoGates} has to look outside for it: a gate to open, or a catalogue on its way. */
    public static boolean waiting(FrontOfficeIntegration i) {
        return i.gate != null || (i.catalogueCommandId != null && i.catalogueSyncedAt == null
                && !i.is(FoIntegrationStatus.DECOMMISSIONED));
    }

    Change recheck(FrontOfficeIntegration i) {
        if (i.is(FoIntegrationStatus.CONNECTIVITY_FAILED)) {
            return verification(i, "recheck");
        }
        if (i.catalogueCommandId != null && i.catalogueSyncedAt == null && !i.is(FoIntegrationStatus.DECOMMISSIONED)) {
            return catalogueCheck(i);
        }
        return Change.NONE;
    }

    public void recheckAutomatically(String id) {
        var i = find(id);
        save(i, recheck(i));
    }

    @Transactional
    public void signalGate(String id) {
        var i = find(id);
        if (gateOpen(i)) {
            outbox.appendToEngine(new MessageReceived(i.gate, i.processKey, List.of()));
        }
    }

    // ── the work behind the steps ───────────────────────────────────────────

    FrontOfficeIntegration save(FrontOfficeIntegration i, Consumer<FrontOfficeIntegration> change) {
        return writes.write(() -> {
            change.accept(i);
            return integrations.save(i);
        });
    }

    /** The chain's connection to Opera, for this property: every property of the chain lives in one tenant (R24). */
    public OhipConnection connection(FrontOfficeIntegration i) {
        var chain = crsPms.chainConnection();
        return new OhipConnection(i.pmsHotelCode, chain.gatewayUrl(), chain.appKey(), chain.clientId(), chain.clientSecret(),
                chain.enterpriseId());
    }

    /** Tries both ends; what they answered, to record. A connector that does not answer is thrown: retried. */
    Change verification(FrontOfficeIntegration i, String by) {
        var opera = services.verify(connection(i));
        ConnectivityCheck frontOffice;
        try {
            var summary = services.frontOfficeCatalogueSummary(i.frontOfficeUrl);
            frontOffice = new ConnectivityCheck(true, "front office %s reachable%s".formatted(i.frontOfficeUrl,
                    summary == null || summary.pmsHotelCode() == null ? " (no PMS catalogue yet)"
                            : " (holds %s's catalogue)".formatted(summary.pmsHotelCode())));
        } catch (RuntimeException e) {
            frontOffice = new ConnectivityCheck(false, "front office %s unreachable: %s".formatted(i.frontOfficeUrl, e.getMessage()));
        }
        var ok = opera.ok() && frontOffice.ok();
        var message = "Opera: %s; %s".formatted(opera.message(), frontOffice.message());
        return x -> verified(x, ok, message, by);
    }

    void verified(FrontOfficeIntegration i, boolean ok, String message, String by) {
        i.connectivityOk = ok;
        i.connectivityMessage = message;
        i.connectivityCheckedAt = clock.instant();
        if (ok) {
            if (i.is(FoIntegrationStatus.CONNECTIVITY_FAILED)) {
                transition(i, FoIntegrationTransition.CONNECTIVITY_RESTORED, "Connections verified: " + message);
            } else {
                i.record(clock.instant(), by, "Connections verified: " + message);
            }
        } else if (i.is(FoIntegrationStatus.CREATED)) {
            transition(i, FoIntegrationTransition.CONNECTIVITY_FAILS, "A connection does not work: " + message);
            notifyAttention(i, "The front office integration of " + i.pmsHotelCode + " cannot reach both ends",
                    message + ". Fix the front office's address or the connection to Opera; the onboarding goes on once both answer.");
        }
    }

    /** Whether the front office holds the catalogue last sent: to record. */
    Change catalogueCheck(FrontOfficeIntegration i) {
        var summary = services.frontOfficeCatalogueSummary(i.frontOfficeUrl);
        return x -> catalogueChecked(x, summary);
    }

    void catalogueChecked(FrontOfficeIntegration i, FrontOfficeCatalogueSummary summary) {
        if (summary == null || i.catalogueCommandId == null || !i.catalogueCommandId.equals(summary.commandId())) {
            return;
        }
        i.catalogueSyncedAt = clock.instant();
        i.catalogueSummary = describe(summary);
        i.record(clock.instant(), "recheck", "The front office holds the PMS's catalogue: " + i.catalogueSummary);
    }

    static String describe(FrontOfficeCatalogueSummary summary) {
        Map<String, Integer> counts = summary.counts() == null ? Map.of() : summary.counts();
        return "%s: %d room types, %d rate plans, %d packages, %d rooms".formatted(summary.pmsHotelCode(),
                counts.getOrDefault("ROOM_TYPE", 0), counts.getOrDefault("RATE_PLAN", 0),
                counts.getOrDefault("PACKAGE", 0), counts.getOrDefault("ROOM", 0));
    }

    void sendCatalogue(FrontOfficeIntegration i, List<FrontOfficeCommand.CatalogueEntry> entries, String by) {
        var command = new FrontOfficeCommand.ReplaceCatalogue(UUID.randomUUID().toString(), i.pmsHotelCode, entries);
        outbox.appendToFrontOffice(command);
        i.catalogueCommandId = command.commandId();
        i.catalogueRequestedAt = clock.instant();
        i.catalogueSyncedAt = null;
        i.record(clock.instant(), by, "The PMS's catalogue sent to the front office: %d entries".formatted(entries.size()));
    }

    /** The property's reservations in the integration's window: in the house now, or arriving within the horizon. */
    List<PmsReservationStamp> window(FrontOfficeIntegration i, String modifiedSince) {
        var today = LocalDate.now(clock);
        var stamps = services.pmsReservations(i.pmsHotelCode, today, today.plusDays(i.horizonDays), i.scope.name(), modifiedSince);
        return stamps == null ? List.of() : stamps;
    }

    FoBackfillRun newRun(FrontOfficeIntegration i, List<PmsReservationStamp> stamps, boolean onboarding, String by) {
        var sorted = new ArrayList<>(stamps);
        sorted.sort(Comparator.comparing(PmsReservationStamp::arrival, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PmsReservationStamp::pmsReservationId));
        var run = new FoBackfillRun();
        run.id = UUID.randomUUID().toString();
        run.integrationId = i.id;
        run.pmsHotelCode = i.pmsHotelCode;
        run.status = FoBackfillRun.Status.RUNNING;
        run.onboarding = onboarding;
        run.pending = new ArrayList<>(sorted.stream()
                .map(s -> new FoBackfillRun.Item(s.pmsReservationId(), s.lastModified())).toList());
        run.expected = sorted.size();
        run.cursorAtStart = stamps.stream().map(PmsReservationStamp::lastModified).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        run.startedAt = clock.instant();
        run.startedBy = by;
        return runs.save(run);
    }

    void transition(FrontOfficeIntegration i, FoIntegrationTransition t, String what) {
        transition(i, t, "onboarding", what);
    }

    void transition(FrontOfficeIntegration i, FoIntegrationTransition t, String by, String what) {
        var from = i.getStatus();
        var to = i.apply(t);
        log.info("pms-fo integration {} ({}): {} -> {}", i.id, i.pmsHotelCode, from, to);
        i.record(clock.instant(), by, what);
        outbox.appendResolution(subject(i), "onboarding");
        if (to == FoIntegrationStatus.READY_TO_ACTIVATE) {
            notifyAttention(i, "The front office of " + i.pmsHotelCode + " is ready to activate",
                    "The front office %s holds the property's reservations of the next %d days. Activating switches on every change of the PMS."
                            .formatted(i.frontOfficeCode, i.horizonDays));
        }
    }

    boolean abandoned(FrontOfficeIntegration i, String step) {
        if (i.is(FoIntegrationStatus.DECOMMISSIONED)) {
            log.info("Step {} of pms-fo integration {} ({}) skipped: it is decommissioned", step, i.id, i.pmsHotelCode);
            return true;
        }
        return false;
    }

    static String subject(FrontOfficeIntegration i) {
        return "integration/fo-" + i.pmsHotelCode;
    }

    void notifyAttention(FrontOfficeIntegration i, String title, String body) {
        outbox.appendNotification(new NotificationRequested(UUID.randomUUID().toString(),
                NotificationType.INTEGRATION_NEEDS_ATTENTION, i.frontOfficeCode, subject(i), title, body,
                properties.consoleUrl() + "/integrations/frontoffice/" + i.pmsHotelCode,
                "fo-integration:" + i.id + ":" + i.getStatus() + ":" + clock.millis(), clock.instant()));
    }

    public FrontOfficeIntegration find(String id) {
        return integrations.findById(id).orElseThrow(() -> new NoSuchElementException("No front office integration " + id));
    }

    /** The one of a property that is not decommissioned, else its latest. */
    public FrontOfficeIntegration byProperty(String pmsHotelCode) {
        return integrations.findFirstByPmsHotelCodeAndStatusNot(pmsHotelCode, FoIntegrationStatus.DECOMMISSIONED)
                .or(() -> integrations.findByPmsHotelCodeOrderByCreatedAtDesc(pmsHotelCode).stream().findFirst())
                .orElseThrow(() -> new NoSuchElementException("No front office integration for property " + pmsHotelCode));
    }

    static void require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A front office integration needs " + what);
        }
    }

    static String blankOr(String value, String otherwise) {
        return value == null || value.isBlank() ? otherwise : value.trim();
    }
}
