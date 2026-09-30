package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChanges;
import io.mateu.ecdemo1.frontoffice.domain.notice.Notice;
import io.mateu.ecdemo1.frontoffice.domain.notice.Notices;
import io.mateu.ecdemo1.frontoffice.domain.stay.NoticeAcknowledgements;
import io.mateu.ecdemo1.frontoffice.domain.stay.NoticeAcknowledgements.Acknowledgement;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.audit.AuditOutbox;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged;
import io.mateu.ecdemo1.messaging.Inbox;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the desk must know of a stay before letting its guests in or out: the reception notices of the
 * holder and of every companion who is a chain customer (Salesforce's), of the reservation itself and
 * of the agency that sold it — all sent by the notices service (notices) — of this hotel or the chain's,
 * in force some day of the stay. At check-in, a BLOCKING one must be read — «He leído el aviso» —
 * before the check-in. At check-out, the kárdex changes Salesforce rejected or has not decided (the
 * invoice goes out with the data it has) and the check-out notices: «Entendido» before the check-out.
 * The desk also sees the ones for preparing the arrival and for the stay, where it works on them.
 *
 * <p>Enforced here, not on the screens: {@link CheckInService} and {@link CheckOutService} refuse
 * while what applies is not acknowledged — by whoever asks, the desk or the reception agent. An
 * acknowledgement covers what was read then (its fingerprint); a warning that changes after it needs
 * another. Acknowledgements and refusals are audited.
 */
@Service
public class GuestNotices {

  static final Logger log = LoggerFactory.getLogger(GuestNotices.class);
  public static final String CONSUMER = "notices";
  static final JsonMapper JSON = JsonMapper.builder().build();

  /** Refused: something that must be acknowledged is not. The message says what, for the desk. */
  public static class NotAcknowledged extends IllegalStateException implements StayAudit.AuditedRefusal {
    public NotAcknowledged(String message) {
      super(message);
    }
  }

  /**
   * A notice, on what it is about: a pax (1 is the holder) with their name, or — pax 0 — the reservation
   * or its agency, with how the desk names them («Reserva 12E45», «Agencia Nordic Travel»).
   */
  public record PaxNotice(int pax, String guestName, Notice notice) {

    /** On a guest, not on the reservation or its agency. */
    public boolean onPax() {
      return pax > 0;
    }
  }

  /** A pax's kárdex change Salesforce rejected or has not decided, and what it means, line by line. */
  public record KardexWarning(int pax, String guestName, KardexChange.KardexStatus status, String reason,
                              List<String> lines) {

    public boolean rejected() {
      return status == KardexChange.KardexStatus.REJECTED;
    }
  }

  /** What the check-out warns of; nothing to acknowledge when it is empty. */
  public record CheckOutWarnings(List<KardexWarning> kardex, List<PaxNotice> notices, String fingerprint) {

    public boolean any() {
      return !kardex.isEmpty() || !notices.isEmpty();
    }
  }

  final StayRepository stays;
  final GuestRepository guests;
  final Notices notices;
  final WalkIns walkIns;
  final KardexChanges kardex;
  final NoticeAcknowledgements acknowledgements;
  final AuditOutbox audit;
  final Inbox inbox;
  final TransactionTemplate apart;
  final String hotel;
  final Clock clock;

  @Autowired
  public GuestNotices(StayRepository stays, GuestRepository guests, Notices notices, WalkIns walkIns,
                      KardexChanges kardex, NoticeAcknowledgements acknowledgements, AuditOutbox audit, Inbox inbox,
                      PlatformTransactionManager transactions, @Value("${frontoffice.hotel:MRU01}") String hotel) {
    this(stays, guests, notices, walkIns, kardex, acknowledgements, audit, inbox, transactions, hotel,
        Clock.systemUTC());
  }

  GuestNotices(StayRepository stays, GuestRepository guests, Notices notices, WalkIns walkIns, KardexChanges kardex,
               NoticeAcknowledgements acknowledgements, AuditOutbox audit, Inbox inbox,
               PlatformTransactionManager transactions, String hotel, Clock clock) {
    this.stays = stays;
    this.guests = guests;
    this.notices = notices;
    this.walkIns = walkIns;
    this.kardex = kardex;
    this.acknowledgements = acknowledgements;
    this.audit = audit;
    this.inbox = inbox;
    this.apart = new TransactionTemplate(transactions);
    this.apart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.hotel = hotel;
    this.clock = clock;
  }

  // ── from the notices service ────────────────────────────────────────────────

  /** A notice as the notices service sends it: kept unless an equal or newer one is. Once per event. */
  @Transactional
  public boolean take(NoticeChanged e) {
    if (e.eventId() != null && !inbox.firstTime(CONSUMER, e.eventId())) {
      return false;
    }
    var moments = EnumSet.noneOf(Notice.Moment.class);
    if (e.moments() != null) {
      e.moments().forEach(m -> {
        var moment = Notice.moment(m.name());
        if (moment != null) {
          moments.add(moment);
        }
      });
    }
    var kept = notices.save(new Notice(e.noticeId(),
        e.subjectType() == null ? Notice.Subject.CUSTOMER : Notice.Subject.valueOf(e.subjectType().name()),
        e.subjectId(), e.subjectName(), e.hotelCode(), e.version(), e.text(),
        e.type() == null ? Notice.Type.INFORMATIVE : Notice.Type.valueOf(e.type().name()),
        e.from(), e.to(), moments, e.active(), e.occurredAt()));
    if (kept) {
      log.info("{} {}: notice {} v{} ({}, {})", e.subjectType(), e.subjectId(), e.noticeId(), e.version(), e.type(),
          e.active() ? "active" : "inactive");
    }
    return kept;
  }

  // ── what applies ────────────────────────────────────────────────────────────

  /** The active notices of the stay shown at that moment: pax by pax, then the reservation's and its agency's. */
  public List<PaxNotice> forStay(Stay stay, Notice.Moment moment) {
    return all(stay).stream()
        .filter(p -> p.notice().appliesTo(hotel, moment, stay.checkIn(), stay.checkOut()))
        .toList();
  }

  /** The active notices of the stay shown at any of these moments, each once. */
  public List<PaxNotice> forStay(Stay stay, Notice.Moment first, Notice.Moment... others) {
    var moments = EnumSet.of(first, others);
    return all(stay).stream()
        .filter(p -> moments.stream().anyMatch(m -> p.notice().appliesTo(hotel, m, stay.checkIn(), stay.checkOut())))
        .toList();
  }

  /** Every active notice of the stay, whatever the moment: what the agent is told. */
  public List<PaxNotice> activeForStay(Stay stay) {
    return forStay(stay, Notice.Moment.PRE_ARRIVAL, Notice.Moment.values());
  }

  List<PaxNotice> all(Stay stay) {
    var result = new ArrayList<PaxNotice>();
    var byCustomer = paxByCustomer(stay);
    if (!byCustomer.isEmpty()) {
      for (var n : notices.of(Notice.Subject.CUSTOMER, byCustomer.keySet())) {
        var pax = byCustomer.get(n.subjectId());
        if (pax != null) {
          result.add(new PaxNotice(pax.number(), pax.name(), n));
        }
      }
    }
    var locators = locators(stay);
    for (var n : notices.of(Notice.Subject.RESERVATION, locators)) {
      result.add(new PaxNotice(0, "Reserva " + n.subjectId(), n));
    }
    for (var n : partnerNotices(stay)) {
      result.add(new PaxNotice(0, "Agencia " + (n.subjectName() == null ? n.subjectId() : n.subjectName()), n));
    }
    result.sort((a, b) -> a.pax() != b.pax() ? Integer.compare(order(a), order(b))
        : Integer.compare(b.notice().type().ordinal(), a.notice().type().ordinal()));
    return result;
  }

  /** The guests first, in their order; then what is on the reservation and on its agency. */
  static int order(PaxNotice p) {
    return p.pax() > 0 ? p.pax() : Integer.MAX_VALUE;
  }

  /**
   * How the CRS knows the reservation: the stay's id — the CRS's locator, for one that came down the
   * chain — and, for a walk-in the desk opened, the locator the CRS gave it once it booked it.
   */
  List<String> locators(Stay stay) {
    var locators = new ArrayList<String>();
    locators.add(stay.id());
    walkIns.of(stay.id()).map(WalkIn::locator).filter(l -> l != null && !l.isBlank()).ifPresent(locators::add);
    return locators.stream().map(l -> l.toUpperCase(java.util.Locale.ROOT)).distinct().toList();
  }

  /**
   * The notices of the agency that sold the stay. The PMS gives the agency by name (the travel agent or
   * company on the reservation), so a partner's notice — which carries the partner's name as the ERP has
   * it, the name its PMS profile was written with — is matched by name, or by code if that is what the
   * stay says.
   */
  List<Notice> partnerNotices(Stay stay) {
    var agency = stay.agency();
    if (agency == null || agency.isBlank()) {
      return List.of();
    }
    var name = agency.trim();
    return notices.active(Notice.Subject.PARTNER).stream()
        .filter(n -> name.equalsIgnoreCase(n.subjectId())
            || (n.subjectName() != null && name.equalsIgnoreCase(n.subjectName().trim())))
        .toList();
  }

  record Pax(int number, String name) {}

  /** The holder (1) and the companions (2…) who are chain customers, by their customer code. */
  LinkedHashMap<String, Pax> paxByCustomer(Stay stay) {
    var map = new LinkedHashMap<String, Pax>();
    guests.findById(stay.guestId()).ifPresent(g -> map.put(g.id(), new Pax(1, g.name())));
    for (int i = 0; i < stay.companions().size(); i++) {
      var c = stay.companions().get(i);
      if (c.companionId() != null && c.companionId().startsWith("C-")) {
        map.putIfAbsent(c.companionId(), new Pax(i + 2, c.name()));
      }
    }
    return map;
  }

  /** The blocking check-in notices: what must be read before the check-in. */
  public List<PaxNotice> blockingAtCheckIn(Stay stay) {
    return forStay(stay, Notice.Moment.CHECK_IN).stream().filter(p -> p.notice().blocking()).toList();
  }

  public String checkInFingerprint(Stay stay) {
    return String.join(",", blockingAtCheckIn(stay).stream().map(p -> p.notice().fingerprint()).sorted().toList());
  }

  /** Whether the check-in can go: no blocking notice, or the desk read the ones there are now. */
  public boolean checkInAcknowledged(Stay stay) {
    return acknowledged(stay.id(), NoticeAcknowledgements.Moment.CHECK_IN, checkInFingerprint(stay));
  }

  /** The kárdex changes Salesforce rejected or has not decided, of every pax who has one. */
  public List<KardexWarning> kardexWarnings(Stay stay) {
    var result = new ArrayList<KardexWarning>();
    for (var entry : paxByCustomer(stay).entrySet()) {
      var change = kardex.of(entry.getKey()).filter(KardexChange::marked).orElse(null);
      if (change == null) {
        continue;
      }
      var guest = entry.getValue().number() == 1 ? guests.findById(entry.getKey()).orElse(null) : null;
      result.add(new KardexWarning(entry.getValue().number(), entry.getValue().name(), change.status(),
          change.reason(), warningLines(change, guest)));
    }
    return result;
  }

  /** Field by field, what the invoice will carry and why. */
  static List<String> warningLines(KardexChange change, Guest guest) {
    var lines = new ArrayList<String>();
    for (var f : change.shownFor(guest)) {
      var kept = guest == null ? f.before() : guest.valueOf(f.field());
      if (change.pending()) {
        lines.add(f.label() + ": propuesto «" + shown(f.after()) + "», pendiente de Salesforce — la factura saldrá con "
            + "el dato anterior («" + shown(f.before()) + "»)");
      } else {
        lines.add(f.label() + ": propuesto «" + shown(f.after()) + "», rechazado por Salesforce — se queda «"
            + shown(kept) + "»");
      }
    }
    if (lines.isEmpty() && change.changes() != null && !change.changes().isBlank()) {
      lines.add(change.label() + ": " + change.changes());
    }
    if (change.status() == KardexChange.KardexStatus.REJECTED && change.reason() != null && !change.reason().isBlank()) {
      lines.add("Motivo: " + change.reason());
    }
    return lines;
  }

  public CheckOutWarnings checkOutWarnings(Stay stay) {
    var k = kardexWarnings(stay);
    var n = forStay(stay, Notice.Moment.CHECK_OUT);
    var parts = new ArrayList<String>();
    n.forEach(p -> parts.add(p.notice().fingerprint()));
    for (var entry : paxByCustomer(stay).keySet()) {
      kardex.of(entry).filter(KardexChange::marked)
          .ifPresent(c -> parts.add(entry + ":" + c.status() + ":" + c.requestId() + ":" + c.requestedAt()));
    }
    return new CheckOutWarnings(k, n, String.join(",", parts.stream().sorted().toList()));
  }

  public boolean checkOutAcknowledged(Stay stay) {
    return acknowledged(stay.id(), NoticeAcknowledgements.Moment.CHECK_OUT, checkOutWarnings(stay).fingerprint());
  }

  public Optional<Acknowledgement> acknowledgement(String stayId, NoticeAcknowledgements.Moment moment) {
    return acknowledgements.of(stayId, moment);
  }

  boolean acknowledged(String stayId, NoticeAcknowledgements.Moment moment, String fingerprint) {
    return fingerprint.isEmpty() || acknowledgements.of(stayId, moment)
        .map(a -> fingerprint.equals(a.fingerprint())).orElse(false);
  }

  // ── acknowledgements ────────────────────────────────────────────────────────

  /**
   * «He leído el aviso»: the desk read the stay's blocking check-in notices as they are now — or, from
   * the agent, as they were when it prepared the check-in ({@code expected}): if they changed since,
   * refused. Audited.
   */
  @Transactional
  public Acknowledgement acknowledgeCheckIn(String stayId, String by, String expected) {
    var stay = stay(stayId);
    var fingerprint = checkInFingerprint(stay);
    if (expected != null && !expected.equals(fingerprint)) {
      throw new NotAcknowledged("Los avisos bloqueantes de la estancia han cambiado desde que se prepararon: "
          + "vuelve a leerlos");
    }
    var texts = blockingAtCheckIn(stay).stream().map(p -> p.guestName() + ": " + p.notice().text()).toList();
    return acknowledge(stay, NoticeAcknowledgements.Moment.CHECK_IN, fingerprint, by,
        "Read check-in notices", "He leído el aviso — " + String.join(" | ", texts));
  }

  /** «Entendido»: the desk read the check-out warnings as they are now (or as prepared). Audited. */
  @Transactional
  public Acknowledgement acknowledgeCheckOut(String stayId, String by, String expected) {
    var stay = stay(stayId);
    var warnings = checkOutWarnings(stay);
    if (expected != null && !expected.equals(warnings.fingerprint())) {
      throw new NotAcknowledged("Los avisos de salida de la estancia han cambiado desde que se prepararon: "
          + "vuelve a leerlos");
    }
    var said = new ArrayList<String>();
    warnings.kardex().forEach(k -> said.add(k.guestName() + ": kárdex " + k.status()));
    warnings.notices().forEach(p -> said.add(p.guestName() + ": " + p.notice().text()));
    return acknowledge(stay, NoticeAcknowledgements.Moment.CHECK_OUT, warnings.fingerprint(), by,
        "Read check-out warnings", "Entendido — " + String.join(" | ", said));
  }

  Acknowledgement acknowledge(Stay stay, NoticeAcknowledgements.Moment moment, String fingerprint, String by,
                              String action, String response) {
    var ack = new Acknowledgement(stay.id(), moment, fingerprint, by, clock.instant());
    acknowledgements.save(ack);
    record(action, stay.id(), by, fingerprint, true, response);
    log.info("{}: {} by {} ({})", stay.id(), action, by, fingerprint);
    return ack;
  }

  // ── what the check-in and the check-out require ─────────────────────────────

  /** Refuses the check-in while a blocking notice is unread — and audits the refusal. */
  public void requireCheckIn(Stay stay, String by) {
    if (!checkInAcknowledged(stay)) {
      var texts = blockingAtCheckIn(stay).stream().map(p -> "«" + p.notice().text() + "» (" + p.guestName() + ")").toList();
      var why = "Aviso bloqueante sin leer: " + String.join("; ", texts) + ". Hay que marcar «He leído el aviso» antes "
          + "de confirmar el check-in.";
      refused("Check-in refused: unread notices", stay.id(), by, checkInFingerprint(stay), why);
      throw new NotAcknowledged(why);
    }
  }

  /** Refuses the check-out while its warnings are not acknowledged — and audits the refusal. */
  public void requireCheckOut(Stay stay, String by) {
    var warnings = checkOutWarnings(stay);
    if (warnings.any() && !acknowledged(stay.id(), NoticeAcknowledgements.Moment.CHECK_OUT, warnings.fingerprint())) {
      var why = "La salida tiene avisos sin confirmar (" + warnings.kardex().size() + " de kárdex, "
          + warnings.notices().size() + " de salida). Hay que pulsar «Entendido» antes de confirmar el check-out.";
      refused("Check-out refused: unread warnings", stay.id(), by, warnings.fingerprint(), why);
      throw new NotAcknowledged(why);
    }
  }

  /** The refusal is recorded apart: the use case's transaction is rolled back with it. */
  void refused(String action, String stayId, String by, String fingerprint, String why) {
    try {
      apart.executeWithoutResult(s -> record(action, stayId, by, fingerprint, false, why));
    } catch (RuntimeException e) {
      log.error("{} of {} could not be audited", action, stayId, e);
    }
  }

  void record(String action, String stayId, String by, String fingerprint, boolean succeeded, String response) {
    var params = new LinkedHashMap<String, Object>();
    params.put("stayId", stayId);
    params.put("covers", fingerprint);
    audit.append(new AuditedAction(UUID.randomUUID().toString(), clock.instant(), AuditOutbox.SERVICE, action, hotel,
        StayAudit.actor(by), JSON.writeValueAsString(params), succeeded, response));
  }

  Stay stay(String stayId) {
    return stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
  }

  static String shown(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

}
