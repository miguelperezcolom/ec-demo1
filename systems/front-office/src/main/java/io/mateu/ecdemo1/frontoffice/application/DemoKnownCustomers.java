package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory;
import io.mateu.ecdemo1.frontoffice.domain.customer.LoyaltyStatus;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestTier;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.scanner.DemoDocuments;
import io.mateu.ecdemo1.frontoffice.infra.scanner.DemoScanner;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The demo's returning customers ({@code ec1.py seed known-customers}): a few holders of this hotel's
 * arrivals and in-house stays who are customers of the chain ({@code C-…}) become customers the desk
 * recognises — the MDM gets the very document the demo scanner will read of them and their Riu Class
 * number, the customer history some past stays, the loyalty service their membership. Everything is
 * derived from the customer's code and name, so seeding again gives the same and changes nothing.
 */
@Service
public class DemoKnownCustomers {

  static final Logger log = LoggerFactory.getLogger(DemoKnownCustomers.class);

  public record Seeded(String stayId, String guestName, String customerId, String riuClass, String documentType,
                       String documentNumber, java.time.LocalDate birthDate) {}

  final StayRepository stays;
  final GuestRepository guests;
  final WalkIns walkIns;
  final DemoScanner scanner;
  final CustomerDirectory directory;
  final StayHistory history;
  final LoyaltyStatus loyalty;

  public DemoKnownCustomers(StayRepository stays, GuestRepository guests, WalkIns walkIns, DemoScanner scanner,
                            CustomerDirectory directory, StayHistory history, LoyaltyStatus loyalty) {
    this.stays = stays;
    this.guests = guests;
    this.walkIns = walkIns;
    this.scanner = scanner;
    this.directory = directory;
    this.history = history;
    this.loyalty = loyalty;
  }

  /** Up to {@code count} stays — arrivals from today on first, then the ones in the house. */
  public List<Seeded> seed(int count) {
    var today = LocalDate.now();
    var picked = new LinkedHashMap<String, Stay>();
    Stream.concat(stays.findArrivals().stream().filter(s -> !s.checkIn().isBefore(today)), stays.findInHouse().stream())
        .filter(s -> s.guestId().startsWith("C-"))
        .forEach(s -> picked.putIfAbsent(s.id(), s));
    var seeded = new ArrayList<Seeded>();
    var codes = new java.util.HashSet<String>();
    for (var stay : picked.values()) {
      if (seeded.size() >= Math.max(0, count)) {
        break;
      }
      var guest = guests.findById(stay.guestId()).orElse(null);
      if (guest == null || !codes.add(guest.id())) {
        continue;
      }
      var code = guest.id();
      // the very document the demo scanner reads of the holder: scanning it, the desk finds them
      var locator = walkIns.of(stay.id()).map(w -> w.locator() == null ? stay.id() : w.locator()).orElse(stay.id());
      var document = scanner.scan(new DemoScanner.Pax(locator, 1, guest.name(), guest.document(), code, stay.checkIn()));
      // The demo's document comes from the name: a holder whose document is already another customer's —
      // the same person, known under another code (a returning guest's new booking) — or several's is
      // left alone. Seeding them would give one document to two customers, and nobody would be recognised.
      var owner = directory.lookup(CustomerDirectory.LookupQuery.byDocument(document.documentNumber(),
          document.issuingCountry()));
      if (owner.outcome() == CustomerDirectory.Outcome.AMBIGUOUS
          || (owner.outcome() == CustomerDirectory.Outcome.FOUND && !code.equals(owner.customer().customerId()))) {
        log.info("Known customer for the demo: {} ({}) skipped — their document is {}'s", guest.name(), code,
            owner.outcome() == CustomerDirectory.Outcome.AMBIGUOUS ? "several customers" : owner.customer().customerId());
        continue;
      }
      var member = memberNumber(code);
      var seed = DemoDocuments.hash("loyalty:" + code);
      var tier = Math.floorMod(seed, 2L) == 0 ? GuestTier.GOLD : GuestTier.PLATINUM;
      var points = 2_000 + 50 * (int) Math.floorMod(seed >>> 3, 760L);
      var since = LocalDate.of(2012 + (int) Math.floorMod(seed >>> 13, 12L), 1 + (int) Math.floorMod(seed >>> 17, 12L), 1);
      var documented = directory.addDocument(code, document.documentType(), document.documentNumber(),
          document.issuingCountry(), document.expiry(), "RESERVATION", document.birthDate(), document.nationality());
      var xref = directory.setRiuClass(code, member);
      var enrolled = loyalty.enroll(member, code, tier.name(), points, since);
      var stayed = history.seedDemo(code, 4 + (int) Math.floorMod(seed >>> 29, 3L));
      // the headers that still read the guest's own figures agree with the loyalty service
      guests.save(guest.withLoyalty(tier, points));
      log.info("Known customer for the demo: {} ({}) — document {} {}, Riu Class {} {}: MDM {}/{}, loyalty {}, history {}",
          guest.name(), code, document.documentType(), document.documentNumber(), member, tier, documented, xref,
          enrolled, stayed);
      seeded.add(new Seeded(stay.id(), guest.name(), code, member, document.documentType(), document.documentNumber(),
          document.birthDate()));
    }
    return seeded;
  }

  /** The customer's Riu Class number: {@code RC} and eight digits, from their code. */
  public static String memberNumber(String customerCode) {
    return String.format("RC%08d", Math.floorMod(DemoDocuments.hash("riuclass:" + customerCode), 100_000_000L));
  }
}
