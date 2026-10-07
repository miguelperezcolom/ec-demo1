package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInChecklist;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.ForcedCheckIn;
import io.mateu.ecdemo1.frontoffice.domain.stay.ForcedCheckIns;
import io.mateu.ecdemo1.frontoffice.domain.stay.PendingStep;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.infra.audit.AuditOutbox;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationResolved;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.messaging.Outbox;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The check-ins the desk forced with steps missing — «Check-in incompleto». A forced check-in lets the
 * guest in (and goes up to the PMS like any other), but the stay owes its missing steps: the pax's
 * documents and the registration's signature. Until they are done the stay cannot check out — refused
 * here, whoever asks — and once the traveller's-registration deadline passes (24 h from the arrival,
 * configurable) with a document still missing, reception is told once, through the communication
 * service's inbox. Forcing, completing and every refusal are audited.
 */
@Service
public class IncompleteCheckIns {

  static final Logger log = LoggerFactory.getLogger(IncompleteCheckIns.class);
  static final JsonMapper JSON = JsonMapper.builder().build();
  static final String NOTIFICATIONS = "notifications";
  static final String RESOLUTIONS = "notification-resolutions";
  static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("es"));

  /** Refused: the stay's check-in is incomplete. The message says what is missing, for the desk. */
  public static class CheckInIncomplete extends IllegalStateException implements StayAudit.AuditedRefusal {
    final List<PendingStep> missing;

    public CheckInIncomplete(String message, List<PendingStep> missing) {
      super(message);
      this.missing = List.copyOf(missing);
    }

    public List<PendingStep> missing() {
      return missing;
    }
  }

  /**
   * Where a stay's check-in stands: its forced check-in (none if it was not forced), what it still
   * lacks, and whether the documents' deadline passed with one of them missing.
   */
  public record Status(ForcedCheckIn forced, List<PendingStep> missing, Instant documentsDue, boolean overdue) {

    /** Forced and still owing steps: the stay cannot check out. */
    public boolean incomplete() {
      return forced != null && forced.open() && !missing.isEmpty();
    }

    public boolean documentsMissing() {
      return missing.stream().anyMatch(PendingStep::document);
    }

    public String missingText() {
      return String.join("; ", missing.stream().map(PendingStep::label).toList());
    }
  }

  final StayRepository stays;
  final GuestRepository guests;
  final CheckInOpsRepository checkInOps;
  final ForcedCheckIns forcedCheckIns;
  final AuditOutbox audit;
  final Outbox outbox;
  final TransactionTemplate apart;
  final String hotel;
  final Duration documentDeadline;
  final String consoleUrl;
  final Clock clock;

  @Autowired
  public IncompleteCheckIns(StayRepository stays, GuestRepository guests, CheckInOpsRepository checkInOps,
                            ForcedCheckIns forcedCheckIns, AuditOutbox audit, Outbox outbox,
                            PlatformTransactionManager transactions,
                            @Value("${frontoffice.hotel:MRU01}") String hotel,
                            @Value("${frontoffice.forced-check-in.document-deadline:PT24H}") Duration documentDeadline,
                            @Value("${frontoffice.front-office-url:}") String frontOfficeUrl) {
    this(stays, guests, checkInOps, forcedCheckIns, audit, outbox, transactions, hotel, documentDeadline,
        frontOfficeUrl, Clock.systemUTC());
  }

  IncompleteCheckIns(StayRepository stays, GuestRepository guests, CheckInOpsRepository checkInOps,
                     ForcedCheckIns forcedCheckIns, AuditOutbox audit, Outbox outbox,
                     PlatformTransactionManager transactions, String hotel, Duration documentDeadline,
                     String frontOfficeUrl, Clock clock) {
    this.stays = stays;
    this.guests = guests;
    this.checkInOps = checkInOps;
    this.forcedCheckIns = forcedCheckIns;
    this.audit = audit;
    this.outbox = outbox;
    this.apart = new TransactionTemplate(transactions);
    this.apart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.hotel = hotel;
    this.documentDeadline = documentDeadline;
    this.consoleUrl = frontOfficeUrl == null ? "" : frontOfficeUrl.replaceAll("/+$", "");
    this.clock = clock;
  }

  // ── reads ────────────────────────────────────────────────────────────────────

  /** The destination's registration rules; none (in a test that does not wire them), nothing more is asked. */
  RegistrationRequirementsService registration;

  @Autowired(required = false)
  public void setRegistration(RegistrationRequirementsService registration) {
    this.registration = registration;
  }

  /**
   * What the stay's check-in still lacks: the pax's documents (no-shows aside), the signature, and what
   * the destination's registration rules require of each pax that the kárdex does not have yet.
   */
  public List<PendingStep> missing(Stay stay) {
    var missing = new java.util.ArrayList<>(
        CheckInChecklist.missing(stay, guests.findById(stay.guestId()).orElse(null), checkInOps.of(stay.id())));
    if (registration != null) {
      missing.addAll(registration.missing(stay));
    }
    return List.copyOf(missing);
  }

  public Status status(Stay stay) {
    var forced = forcedCheckIns.of(stay.id()).orElse(null);
    var missing = missing(stay);
    if (forced == null) {
      return new Status(null, missing, null, false);
    }
    var due = forced.documentsDue(documentDeadline);
    var overdue = forced.open() && missing.stream().anyMatch(PendingStep::document) && !clock.instant().isBefore(due);
    return new Status(forced, missing, due, overdue);
  }

  public Status status(String stayId) {
    return status(stay(stayId));
  }

  public Optional<ForcedCheckIn> forced(String stayId) {
    return forcedCheckIns.of(stayId);
  }

  /** The documents' deadline after a forced check-in (24 h, configurable). */
  public Duration documentDeadline() {
    return documentDeadline;
  }

  /** The deadline as a person reads it: «24 h», or «2 min» when it is set short (to try it). */
  public String documentDeadlineLabel() {
    return documentDeadline.toMinutes() < 60 ? documentDeadline.toMinutes() + " min" : documentDeadline.toHours() + " h";
  }

  // ── writes ───────────────────────────────────────────────────────────────────

  /**
   * Records the check-in forced with {@code missing} steps, in the caller's transaction (the check-in's),
   * and audits who, when and why.
   */
  @Transactional
  public ForcedCheckIn recordForced(Stay stay, String reason, List<PendingStep> missing, String by) {
    var who = who(by);
    var forced = forcedCheckIns.save(ForcedCheckIn.forced(stay.id(), who, clock.instant(), reason,
        String.join("; ", missing.stream().map(PendingStep::label).toList())));
    record("Forced check-in", stay.id(), who, Map.of("reason", forced.reason(), "missing", forced.missingWhenForced()),
        true, "Check-in forzado con pasos pendientes: " + forced.missingWhenForced() + ". Motivo: " + forced.reason());
    log.info("{}: check-in forced by {} ({}), missing {}", stay.id(), who, forced.reason(), forced.missingWhenForced());
    return forced;
  }

  /**
   * The desk says the forced check-in is complete («Completar»): it is, if nothing is missing any more —
   * recorded and audited, and reception's notice about it closed. Refused, saying what is missing, if not.
   */
  @Transactional
  public Status complete(String stayId, String by) {
    var stay = stay(stayId);
    var status = status(stay);
    if (status.incomplete()) {
      var why = "Aún falta: " + status.missingText() + ".";
      refused("Complete check-in refused: steps missing", stayId, by, why);
      throw new CheckInIncomplete(why, status.missing());
    }
    settle(stay, by);
    return status(stay);
  }

  /**
   * Closes the stay's forced check-in if its steps are all done now — after a document is scanned or the
   * registration signed. Nothing to do for a stay whose check-in was not forced or is already complete.
   */
  @Transactional
  public boolean settle(String stayId, String by) {
    return stays.findById(stayId).map(stay -> settle(stay, by)).orElse(false);
  }

  boolean settle(Stay stay, String by) {
    var forced = forcedCheckIns.of(stay.id()).filter(ForcedCheckIn::open).orElse(null);
    if (forced == null || !missing(stay).isEmpty()) {
      return false;
    }
    var who = who(by);
    forcedCheckIns.save(forced.completed(who, clock.instant()));
    record("Forced check-in completed", stay.id(), who, Map.of("reason", forced.reason()), true,
        "Check-in completado: ya no falta nada de lo que faltaba (" + forced.missingWhenForced() + ").");
    // the inbox notice about it, if reception was told, is no longer waiting
    var resolution = new NotificationResolved(subject(stay.id()), who, clock.instant());
    outbox.append(RESOLUTIONS, resolution.subject(), "NotificationResolved", JSON.writeValueAsString(resolution), null);
    log.info("{}: forced check-in completed by {}", stay.id(), who);
    return true;
  }

  /**
   * Refuses the check-out of a stay whose forced check-in is still incomplete — and audits the refusal.
   * A forced check-in whose steps were all done meanwhile is closed on the way.
   */
  public void requireComplete(Stay stay, String by) {
    var status = status(stay);
    if (status.incomplete()) {
      var why = "Check-in incompleto: falta " + status.missingText() + ". Complétalo («Completar») antes del "
          + "check-out.";
      refused("Check-out refused: check-in incomplete", stay.id(), by, why);
      throw new CheckInIncomplete(why, status.missing());
    }
    settle(stay, by);
  }

  /** Refuses a normal check-in with steps missing — they are done first, or the check-in is forced. */
  public void requireCompleteCheckIn(Stay stay, String by) {
    var missing = missing(stay);
    if (!missing.isEmpty()) {
      var why = "Faltan pasos del check-in: " + String.join("; ", missing.stream().map(PendingStep::label).toList())
          + ". Complétalos, o fuerza el check-in con un motivo.";
      refused("Check-in refused: steps missing", stay.id(), by, why);
      throw new CheckInIncomplete(why, missing);
    }
  }

  // ── the traveller's-registration deadline ─────────────────────────────────────

  /**
   * Every minute: the forced check-ins whose documents' deadline passed with a document still missing,
   * and whose reception has not been told yet, are told now — once each (the stamp, and the notice's
   * dedup key, make it idempotent).
   */
  @Scheduled(fixedDelayString = "${frontoffice.forced-check-in.check-interval:PT1M}",
      initialDelayString = "${frontoffice.forced-check-in.check-interval:PT1M}")
  public void checkDeadlines() {
    try {
      notifyOverdue(clock.instant());
    } catch (RuntimeException e) {
      log.warn("The forced check-ins' deadlines could not be checked: {}", e.toString());
    }
  }

  /** The overdue ones at {@code now}, each told in its own transaction; how many were told. */
  int notifyOverdue(Instant now) {
    int told = 0;
    for (var forced : forcedCheckIns.open()) {
      if (forced.overdueNotifiedAt() != null || now.isBefore(forced.documentsDue(documentDeadline))) {
        continue;
      }
      var notified = apart.execute(s -> notifyOverdue(forced.stayId(), now));
      if (Boolean.TRUE.equals(notified)) {
        told++;
      }
    }
    return told;
  }

  boolean notifyOverdue(String stayId, Instant now) {
    var forced = forcedCheckIns.of(stayId).orElse(null);
    var stay = stays.findById(stayId).orElse(null);
    if (forced == null || stay == null || !forced.open() || forced.overdueNotifiedAt() != null) {
      return false;
    }
    var documents = missing(stay).stream().filter(PendingStep::document).toList();
    if (documents.isEmpty()) {
      return false; // only the signature is missing: nothing the registration of travellers waits on
    }
    var guest = guests.findById(stay.guestId()).map(g -> g.name()).orElse(stay.guestId());
    var room = stay.hasRoom() ? "hab. " + stay.roomNumber() : "sin habitación";
    var body = ("El check-in de %s (%s, %s) se forzó el %s por %s — «%s» — y pasadas %s de la llegada sigue "
        + "faltando: %s. El parte de viajeros no puede esperar más: complétalo en la estancia («Completar»). "
        + "La estancia no puede hacer el check-out hasta entonces.").formatted(stay.id(), guest, room,
        WHEN.format(forced.forcedAt().atZone(ZoneId.of("Europe/Madrid"))), forced.forcedBy(), forced.reason(),
        documentDeadlineLabel(), String.join("; ", documents.stream().map(PendingStep::label).toList()));
    var notice = new NotificationRequested(UUID.randomUUID().toString(), NotificationType.CHECK_IN_INCOMPLETE, hotel,
        subject(stayId), "Check-in incompleto: " + stayId + " sin documentación pasadas " + documentDeadlineLabel(), body, consoleUrl.isBlank() ? null : consoleUrl + "/reservas/" + stayId,
        "forced-check-in-overdue:" + stayId, now);
    outbox.append(NOTIFICATIONS, notice.dedupKey(), "NotificationRequested", JSON.writeValueAsString(notice), null);
    forcedCheckIns.save(forced.overdueNotified(now));
    record("Forced check-in overdue: reception told", stayId, "front-office", Map.of("missing", notice.body()), true,
        notice.title());
    log.info("{}: forced check-in overdue — reception told", stayId);
    return true;
  }

  /** What every notice about a stay's forced check-in is about: they all close when it is completed. */
  static String subject(String stayId) {
    return "front-office/forced-check-in/" + stayId;
  }

  // ── audit ────────────────────────────────────────────────────────────────────

  /** The refusal is recorded apart: the use case's transaction is rolled back with it. */
  void refused(String action, String stayId, String by, String why) {
    try {
      apart.executeWithoutResult(s -> record(action, stayId, who(by), Map.of(), false, why));
    } catch (RuntimeException e) {
      log.error("{} of {} could not be audited", action, stayId, e);
    }
  }

  void record(String action, String stayId, String by, Map<String, String> extra, boolean succeeded, String response) {
    var params = new LinkedHashMap<String, Object>();
    params.put("stayId", stayId);
    params.putAll(extra);
    audit.append(new AuditedAction(UUID.randomUUID().toString(), clock.instant(), AuditOutbox.SERVICE, action, hotel,
        by, JSON.writeValueAsString(params), succeeded, response));
  }

  static String who(String by) {
    return StayAudit.actor(by);
  }

  Stay stay(String stayId) {
    return stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("Reserva " + stayId + " no encontrada"));
  }
}
