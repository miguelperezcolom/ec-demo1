package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.customer.ArrivalBriefings;
import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory;
import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory.Lookup;
import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory.LookupQuery;
import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory.Outcome;
import io.mateu.ecdemo1.frontoffice.domain.customer.LoyaltyStatus;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions.Certainty;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions.MatchedBy;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions.Named;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions.PaxRecognition;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions.PaxScan;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory.HistorySummary;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import io.mateu.ecdemo1.frontoffice.infra.scanner.DemoDocuments;
import io.mateu.ecdemo1.integration.model.command.CustomerCommand;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Who the pax at the counter is in the chain — the desk recognises a returning customer, and shows
 * their history and their Riu Class standing:
 *
 * <ul>
 *   <li>a document the chain already knows, of someone with the pax's name: <b>known</b>;</li>
 *   <li>a document of someone with another name, or of more than one customer: only <b>possible</b> —
 *       the desk asks the guest, and no history is shown;</li>
 *   <li>a document nobody has (a passport instead of the DNI the chain knows): the customers with the
 *       pax's name and birth date are <b>possible</b>; none, nothing is shown;</li>
 *   <li>the desk confirms who the pax is with their Riu Class number or email — certainty —, and the new
 *       document goes to the MDM again, saying whose it is;</li>
 *   <li>a pax the reservation already names by the chain's code ({@code C-…}) is known from the start.</li>
 * </ul>
 *
 * The MDM is asked synchronously but briefly, after the scan's own transaction, and the answer kept
 * ({@link PaxRecognitions}): the check-in never waits long for it and never fails because of it — an
 * MDM that does not answer just recognises nobody.
 */
@Service
public class Recognition {

  static final Logger log = LoggerFactory.getLogger(Recognition.class);

  final CustomerDirectory directory;
  final StayHistory history;
  final LoyaltyStatus loyalty;
  final PaxRecognitions recognitions;
  final ArrivalBriefings briefings;
  final StayRepository stays;
  final GuestRepository guests;
  final WalkIns walkIns;
  final CommandOutbox outbox;
  final String hotel;
  final TransactionTemplate transaction;
  final Clock clock = Clock.systemUTC();

  public Recognition(CustomerDirectory directory, StayHistory history, LoyaltyStatus loyalty,
                     PaxRecognitions recognitions, ArrivalBriefings briefings, StayRepository stays, GuestRepository guests, WalkIns walkIns,
                     CommandOutbox outbox, @Value("${frontoffice.hotel:MRU01}") String hotel,
                     PlatformTransactionManager transactions) {
    this.directory = directory;
    this.history = history;
    this.loyalty = loyalty;
    this.recognitions = recognitions;
    this.briefings = briefings;
    this.stays = stays;
    this.guests = guests;
    this.walkIns = walkIns;
    this.outbox = outbox;
    this.hotel = hotel;
    this.transaction = new TransactionTemplate(transactions);
  }

  // ── what the desk sees ───────────────────────────────────────────────────────

  /**
   * What the screens show of a pax: nothing; possibly one of the candidates (names and birth dates
   * only); or the customer known, with the summary of their stays and their Riu Class standing — the
   * desk sees a summary, never what they consumed.
   */
  public record View(Certainty certainty, MatchedBy matchedBy, String customerId, String name, List<Named> candidates,
                     Optional<HistorySummary> history, Optional<LoyaltyStatus.Loyalty> loyalty) {

    public static View none() {
      return new View(null, null, null, null, List.of(), Optional.empty(), Optional.empty());
    }

    public boolean known() {
      return certainty == Certainty.KNOWN;
    }

    public boolean possible() {
      return certainty == Certainty.POSSIBLE;
    }

    /** The summary, when the customer is known and has stays in the chain. */
    public Optional<HistorySummary> stays() {
      return known() ? history.filter(HistorySummary::any) : Optional.empty();
    }
  }

  public View view(String stayId, int pax) {
    try {
      var stay = stays.findById(stayId).orElse(null);
      if (stay == null) {
        return View.none();
      }
      var paxId = paxId(stay, pax);
      var row = recognitions.of(stayId, pax).orElse(null);
      if (row != null && row.certainty() == Certainty.KNOWN) {
        return known(stayId, pax, row.customerId(), row.customerName(), row.matchedBy(), row.riuClass());
      }
      if (row != null) {
        // what the last scan or search said wins over the reservation's code: a provisional C-… whose
        // passport points at another customer is exactly the case to ask about
        return new View(Certainty.POSSIBLE, row.matchedBy(), row.customerId(), row.customerName(), row.candidates(),
            Optional.empty(), Optional.empty());
      }
      // the briefing's customer may be the one the pax's Opera profile is, not their id here
      var briefed = briefings.of(stayId, pax);
      if (briefed.isPresent()) {
        // prepared before they arrived: known, with no service asked now
        return briefed(briefed.get(), MatchedBy.CHAIN_CODE);
      }
      if (chainKnown(paxId)) {
        // the reservation names them by a chain customer who has stayed with us: known without a scan
        return known(stayId, pax, paxId, paxName(stay, pax), MatchedBy.CHAIN_CODE, null);
      }
      return View.none();
    } catch (RuntimeException e) {
      log.info("{}: pax {} could not be recognised ({})", stayId, pax, e.getMessage());
      return View.none();
    }
  }

  /** Known: what the arrivals' briefing prepared of this customer, if it did; else asked now. */
  View known(String stayId, int pax, String customerId, String name, MatchedBy by, String riuClass) {
    var briefed = briefings.of(stayId, pax).filter(b -> b.customerId().equals(customerId));
    if (briefed.isPresent() && (riuClass == null || briefed.get().loyalty() != null)) {
      return briefed(briefed.get(), by);
    }
    return known(customerId, name, by, riuClass);
  }

  static View briefed(ArrivalBriefings.Briefing b, MatchedBy by) {
    return new View(Certainty.KNOWN, by, b.customerId(), b.customerName(), List.of(), Optional.of(b.history()),
        b.riuClass());
  }

  View known(String customerId, String name, MatchedBy by, String riuClass) {
    return new View(Certainty.KNOWN, by, customerId, name, List.of(), history.summary(customerId),
        loyalty.of(customerId, riuClass));
  }

  /**
   * Whether the scan just done left something for the desk to see about this pax — a customer
   * recognised or possible —: the check-in then stays on the pax instead of going on to the next.
   */
  public boolean hasNews(String stayId, int pax) {
    try {
      return recognitions.of(stayId, pax).isPresent();
    } catch (RuntimeException e) {
      return false;
    }
  }

  // ── after a scan ─────────────────────────────────────────────────────────────

  /**
   * The desk scanned the pax's document (its own transaction is done): the MDM is asked whose it is.
   * Never throws: an MDM that does not answer, or anything else, recognises nobody.
   */
  public void afterScan(String stayId, int pax, DemoDocuments.Scanned scanned) {
    try {
      recognise(stayId, pax, scanned);
    } catch (RuntimeException e) {
      log.warn("{}: pax {} scanned, not recognised ({})", stayId, pax, e.getMessage());
    }
  }

  void recognise(String stayId, int pax, DemoDocuments.Scanned scanned) {
    var stay = stays.findById(stayId).orElse(null);
    if (stay == null || scanned == null || scanned.documentNumber() == null) {
      return;
    }
    var paxId = paxId(stay, pax);
    var lookup = directory.lookup(LookupQuery.byDocument(scanned.documentNumber(), scanned.issuingCountry()));
    var found = lookup.outcome() == Outcome.FOUND ? lookup.customer() : null;
    var before = recognitions.of(stayId, pax).orElse(null);
    transaction.executeWithoutResult(status -> {
      recognitions.saveScan(new PaxScan(stayId, pax, scanned.firstName(), scanned.lastName(), scanned.documentType(),
          scanned.documentNumber(), scanned.birthDate(), scanned.nationality(), scanned.issuingCountry(),
          scanned.expiry(), lookup.outcome().name(), found == null ? null : found.customerId()));
      switch (lookup.outcome()) {
        case UNAVAILABLE -> {
          // nothing can be said: what was known stays as it was
        }
        case FOUND -> {
          if (sameName(scanned.firstName(), scanned.lastName(), found.firstName(), found.lastName())) {
            recognitions.save(new PaxRecognition(stayId, pax, found.customerId(), found.name(), Certainty.KNOWN,
                MatchedBy.DOCUMENT, List.of(), null, null, null));
          } else if (keptConfirmed(before) || chainKnown(paxId)) {
            // someone else's document, but the desk already knows who this pax is
            if (!keptConfirmed(before)) recognitions.clear(stayId, pax);
          } else {
            recognitions.save(new PaxRecognition(stayId, pax, found.customerId(), found.name(), Certainty.POSSIBLE,
                MatchedBy.DOCUMENT, List.of(new Named(found.customerId(), found.name(), found.birthDate())), null, null,
                null));
          }
        }
        case AMBIGUOUS -> {
          if (!keptConfirmed(before)) {
            if (chainKnown(paxId)) {
              recognitions.clear(stayId, pax);
            } else {
              recognitions.save(new PaxRecognition(stayId, pax, null, null, Certainty.POSSIBLE, MatchedBy.DOCUMENT,
                  List.of(), null, null, null));
            }
          }
        }
        case NONE -> {
          if (keptConfirmed(before)) {
            // a new document of a pax the desk confirmed: it joins that customer, no more questions
            sendConfirmed(stay, pax, paxId, recognitions.scanOf(stayId, pax).orElseThrow(), before.customerId());
          } else {
            // a document the chain does not know: whoever the reservation names (a provisional C-…, most
            // of the time), someone else with the same name and birth date may be them — asked, never
            // assumed. With nobody else, a C-… pax keeps their code and the MDM adds the document to them
            // by the scan's own command.
            possibleByName(stayId, pax, scanned.firstName(), scanned.lastName(), scanned.birthDate(),
                scanned.nationality(), paxId);
          }
        }
      }
    });
    log.info("{}: pax {} scanned — the MDM says {}{}", stayId, pax, lookup.outcome(),
        found == null ? "" : " (" + found.customerId() + ")");
  }

  static boolean keptConfirmed(PaxRecognition row) {
    return row != null && row.certainty() == Certainty.KNOWN && row.confirmed();
  }

  /** Candidates by name and birth date: possible; none, nothing (and nothing left from before). */
  boolean possibleByName(String stayId, int pax, String first, String last, LocalDate birthDate, String nationality) {
    return possibleByName(stayId, pax, first, last, birthDate, nationality, null);
  }

  /** The same, leaving out the customer the pax already is (their own C-… code is no news). */
  boolean possibleByName(String stayId, int pax, String first, String last, LocalDate birthDate, String nationality,
                         String self) {
    var candidates = directory.candidates(first, last, birthDate, nationality).stream()
        .filter(c -> self == null || !self.equals(c.customerId())).toList();
    if (candidates.isEmpty()) {
      recognitions.clear(stayId, pax);
      return false;
    }
    var named = candidates.stream().limit(5)
        .map(c -> new Named(c.customerId(), c.name(), c.birthDate())).toList();
    recognitions.save(new PaxRecognition(stayId, pax, named.size() == 1 ? named.getFirst().customerId() : null,
        named.size() == 1 ? named.getFirst().name() : null, Certainty.POSSIBLE, MatchedBy.CANDIDATE, named, null, null,
        null));
    return true;
  }

  // ── the desk asks the guest ──────────────────────────────────────────────────

  /** What a confirmation or a search came to, for the desk. */
  public record Answer(boolean done, String message) {}

  /**
   * The desk confirms who the pax is with what the guest says — their Riu Class number or their email:
   * certainty, if the MDM finds one customer by it. If the pax's last document was one the chain did not
   * know, it is sent to the MDM again, saying whose it is: the MDM consolidates the pax's provisional
   * code into that customer and adds the document to theirs.
   */
  public Answer confirm(String stayId, int pax, String by, String riuClassOrEmail) {
    var value = riuClassOrEmail == null ? "" : riuClassOrEmail.trim();
    if (value.isEmpty()) {
      return new Answer(false, "Escribe el número Riu Class o el email del cliente");
    }
    var email = value.contains("@");
    var member = email ? null : memberNumber(value);
    if (!email && member == null) {
      return new Answer(false, "«" + value + "» no parece ni un número Riu Class ni un email");
    }
    try {
      var stay = stays.findById(stayId).orElse(null);
      if (stay == null) {
        return new Answer(false, "Reserva " + stayId + " no encontrada");
      }
      Lookup lookup = directory.lookup(email ? LookupQuery.byEmail(value.toLowerCase(Locale.ROOT))
          : LookupQuery.byRiuClass(member));
      return switch (lookup.outcome()) {
        case UNAVAILABLE -> new Answer(false,
            "El maestro de clientes no responde ahora: el check-in sigue sin reconocer al cliente");
        case NONE -> new Answer(false, "Ningún cliente de la cadena tiene " + (email ? "ese email" : "ese número Riu Class"));
        case AMBIGUOUS -> new Answer(false, "Más de un cliente tiene " + (email ? "ese email" : "ese número Riu Class")
            + ": no se puede confirmar así");
        case FOUND -> {
          var customer = lookup.customer();
          var paxId = paxId(stay, pax);
          var before = recognitions.of(stayId, pax).orElse(null);
          var sent = transaction.execute(status -> {
            recognitions.save(new PaxRecognition(stayId, pax, customer.customerId(), customer.name(), Certainty.KNOWN,
                email ? MatchedBy.EMAIL : MatchedBy.RIU_CLASS, List.of(), clock.instant(), by, member));
            var scan = recognitions.scanOf(stayId, pax).orElse(null);
            if (scan != null && newDocumentOf(scan, before, customer.customerId())) {
              sendConfirmed(stay, pax, paxId, scan, customer.customerId());
              return true;
            }
            return false;
          });
          log.info("{}: pax {} confirmed as {} by {} ({}){}", stayId, pax, customer.customerId(), email ? "email" : "Riu Class",
              by, Boolean.TRUE.equals(sent) ? " — the new document goes to them" : "");
          yield new Answer(true, "Cliente conocido: " + customer.name() + " (" + customer.customerId() + ")"
              + (Boolean.TRUE.equals(sent) ? ". El documento nuevo se añade a su ficha." : ""));
        }
      };
    } catch (RuntimeException e) {
      log.warn("{}: pax {} could not be confirmed ({})", stayId, pax, e.getMessage());
      return new Answer(false, "No se ha podido confirmar ahora: el check-in sigue sin reconocer al cliente");
    }
  }

  /**
   * Whether the last scan is a document to send again as the confirmed customer's: one the chain did not
   * know, or the very customer's whose name did not match — not someone else's, nor an ambiguous one.
   */
  static boolean newDocumentOf(PaxScan scan, PaxRecognition before, String customerId) {
    if (Outcome.NONE.name().equals(scan.lookup())) {
      return true;
    }
    return Outcome.FOUND.name().equals(scan.lookup()) && customerId.equals(scan.foundCustomerId())
        && before != null && before.certainty() == Certainty.POSSIBLE;
  }

  /** The scanned document again, with the customer the desk confirmed: through the outbox, in the caller's transaction. */
  void sendConfirmed(Stay stay, int pax, String paxId, PaxScan scan, String confirmedCustomerId) {
    var command = new CustomerCommand.RecordScannedIdentity("SCAN-" + UUID.randomUUID(), hotel, locatorOf(stay.id()),
        stay.id(), pax, chain(paxId) ? paxId : null, scan.firstName(), scan.lastName(), scan.documentType(),
        scan.documentNumber(), scan.birthDate(), scan.nationality(),
        "front office " + hotel + " · " + stay.id() + " pax " + pax + " · confirmado en recepción",
        scan.issuingCountry(), scan.expiry(), confirmedCustomerId);
    outbox.append(CommandOutbox.CUSTOMER_COMMANDS, command.key(), command);
  }

  /**
   * The desk searches by name and birth date — what the guest says, when there is no document or the
   * scan found nobody: the matching customers are only possible.
   */
  public Answer searchByName(String stayId, int pax, String firstName, String lastName, LocalDate birthDate) {
    if (birthDate == null) {
      return new Answer(false, "Para buscar por nombre hace falta la fecha de nacimiento");
    }
    if (blank(firstName) && blank(lastName)) {
      return new Answer(false, "Escribe el nombre y los apellidos");
    }
    try {
      var stay = stays.findById(stayId).orElse(null);
      if (stay == null) {
        return new Answer(false, "Reserva " + stayId + " no encontrada");
      }
      var paxId = paxId(stay, pax);
      if (chainKnown(paxId) || keptConfirmed(recognitions.of(stayId, pax).orElse(null))) {
        return new Answer(false, "Este huésped ya es un cliente conocido");
      }
      // their own code (a provisional C-…, most of the time) is no candidate
      var found = Boolean.TRUE.equals(transaction.execute(status ->
          possibleByName(stayId, pax, trim(firstName), trim(lastName), birthDate, null, paxId)));
      return found ? new Answer(true, "Posible cliente conocido: pregúntale al huésped y confirma con su número Riu Class o su email")
          : new Answer(false, "Ningún cliente de la cadena con ese nombre y esa fecha de nacimiento");
    } catch (RuntimeException e) {
      log.warn("{}: pax {} could not be searched by name ({})", stayId, pax, e.getMessage());
      return new Answer(false, "No se ha podido buscar ahora: el check-in sigue igual");
    }
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  /** A Riu Class member number as the MDM keeps it ({@code RC} and eight digits); null if it does not look like one. */
  static String memberNumber(String value) {
    var v = value.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    if (v.matches("\\d{6,12}")) {
      return v.length() == 8 ? "RC" + v : v;
    }
    return v.matches("RC\\d{6,12}") ? v : null;
  }

  /**
   * The MDM's criterion when it consolidates a scanned identity (CM-F7): the same name — first and last
   * names together, without accents, in lower case, letters only. A name missing on either side does not
   * contradict.
   */
  static boolean sameName(String first1, String last1, String first2, String last2) {
    var a = key(first1, last1);
    var b = key(first2, last2);
    return a.isEmpty() || b.isEmpty() || a.equals(b);
  }

  static String key(String first, String last) {
    return Normalizer.normalize((first == null ? "" : first) + (last == null ? "" : last), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
  }

  /** The id the front office knows the pax by: the stay's guest, or the companion's. */
  static String paxId(Stay stay, int pax) {
    if (pax <= 1) {
      return stay.guestId();
    }
    var companion = stay.companionAt(pax);
    return companion == null ? null : companion.companionId();
  }

  String paxName(Stay stay, int pax) {
    if (pax <= 1) {
      return guests.findById(stay.guestId()).map(g -> g.name()).orElse(null);
    }
    var companion = stay.companionAt(pax);
    return companion == null ? null : companion.name();
  }

  static boolean chain(String id) {
    return id != null && id.startsWith("C-");
  }
  /**
   * A chain code that names a customer who has stayed with us — not just a code: the MDM gives one to every
   * holder, provisional for the 70 % that arrive unidentified, and a provisional with no stays is nobody the
   * desk knows yet.
   */
  boolean chainKnown(String id) {
    return chain(id) && history.summary(id).filter(HistorySummary::any).isPresent();
  }


  String locatorOf(String stayId) {
    return walkIns.of(stayId).map(w -> w.locator() == null ? stayId : w.locator()).orElse(stayId);
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  static String trim(String s) {
    return s == null ? null : s.trim();
  }
}
