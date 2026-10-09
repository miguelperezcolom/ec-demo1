package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory.Candidate;
import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory.Customer;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions.Certainty;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions.MatchedBy;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory.HistorySummary;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory.LastStay;
import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import io.mateu.ecdemo1.frontoffice.infra.scanner.DemoDocuments;
import io.mateu.ecdemo1.frontoffice.infra.scanner.DemoScanner;
import io.mateu.ecdemo1.messaging.OutboxMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The desk recognises a returning customer when it scans their document — or possibly, and then asks
 * them —, with the MDM, the customer history and Riu Class in memory. The demo scanner, with no
 * booking nor MDM to ask, reads the same made-up document of a name every time: what the MDM double is
 * told to know.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recognition;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@Import(RecognitionTest.Doubles.class)
class RecognitionTest {

  @TestConfiguration
  static class Doubles {
    @Bean @Primary InMemoryCustomers.Directory inMemoryDirectory() {
      return new InMemoryCustomers.Directory();
    }

    @Bean @Primary InMemoryCustomers.History inMemoryHistory() {
      return new InMemoryCustomers.History();
    }

    @Bean @Primary InMemoryCustomers.RiuClass inMemoryLoyalty() {
      return new InMemoryCustomers.RiuClass();
    }
  }

  static final AtomicInteger SEQ = new AtomicInteger();
  static final LocalDate BORN = LocalDate.of(1984, 3, 9);

  @Autowired InMemoryCustomers.Directory mdm;
  @Autowired InMemoryCustomers.History history;
  @Autowired InMemoryCustomers.RiuClass loyalty;
  @Autowired KardexService kardex;
  @Autowired Recognition recognition;
  @Autowired GuestRepository guests;
  @Autowired StayRepository stays;
  @Autowired CommandOutbox outbox;

  final DemoScanner sameScanner = new DemoScanner("", "", "MRU01");

  @BeforeEach
  void clean() {
    mdm.reset();
  }

  /** An arriving stay whose holder the reservation does not name by the chain's code (a provisional guest). */
  String arrival(String guestId, String name) {
    var n = SEQ.incrementAndGet();
    guests.save(Guest.fromReservation(guestId, name, null, null, null));
    var stayId = "REC-" + n;
    stays.save(Stay.fromReservation(stayId, guestId, "Doble", "Desayuno", LocalDate.now(), LocalDate.now().plusDays(4), 1,
        "TUI", new BigDecimal("400.00"), List.of()));
    return stayId;
  }

  /** The document the demo scanner will read of the holder. */
  DemoDocuments.Scanned documentOf(String stayId, String name, boolean newPassport) {
    var pax = new DemoScanner.Pax(stayId, 1, name, null, null, LocalDate.now());
    return newPassport ? sameScanner.scanNewPassport(pax) : sameScanner.scan(pax);
  }

  static HistorySummary fiveStays(String code) {
    return new HistorySummary(code, 5, 23, LocalDate.of(2019, 7, 1), LocalDate.of(2025, 8, 10),
        List.of(new LastStay("MRU01", LocalDate.of(2025, 8, 3), LocalDate.of(2025, 8, 10), "1204", "Doble")), 3, "MRU01",
        new BigDecimal("640.00"), "EUR");
  }

  @Test
  void aDocumentTheChainKnowsOfSomeoneWithTheSameNameIsAKnownCustomerWithTheirHistory() {
    var stayId = arrival("opera-R1", "Marta Soler");
    var doc = documentOf(stayId, "Marta Soler", false);
    mdm.document(doc.documentNumber(), new Customer("C-R1", "ACTIVE", "MARTA", "Solér", doc.birthDate()));
    history.summaries.put("C-R1", fiveStays("C-R1"));
    loyalty.enroll("RC00000001", "C-R1", "GOLD", 12500, LocalDate.of(2019, 1, 1));

    kardex.scanned(stayId, 1);

    var view = recognition.view(stayId, 1);
    assertThat(view.certainty()).isEqualTo(Certainty.KNOWN);
    assertThat(view.matchedBy()).isEqualTo(MatchedBy.DOCUMENT);
    assertThat(view.customerId()).isEqualTo("C-R1");
    assertThat(view.stays()).get().extracting(HistorySummary::stays).isEqualTo(5);
    assertThat(view.loyalty()).get().satisfies(l -> {
      assertThat(l.tier()).isEqualTo("GOLD");
      assertThat(l.points()).isEqualTo(12500);
    });
  }

  @Test
  void aDocumentOfSomeoneWithAnotherNameIsOnlyAPossibleCustomerAndShowsNoHistory() {
    var stayId = arrival("opera-R2", "Marta Soler");
    var doc = documentOf(stayId, "Marta Soler", false);
    mdm.document(doc.documentNumber(), new Customer("C-R2", "ACTIVE", "Pedro", "Ruiz", BORN));
    history.summaries.put("C-R2", fiveStays("C-R2"));

    kardex.scanned(stayId, 1);

    var view = recognition.view(stayId, 1);
    assertThat(view.certainty()).isEqualTo(Certainty.POSSIBLE);
    assertThat(view.candidates()).singleElement().satisfies(c -> {
      assertThat(c.customerId()).isEqualTo("C-R2");
      assertThat(c.name()).isEqualTo("Pedro Ruiz");
    });
    assertThat(view.history()).isEmpty();
    assertThat(view.stays()).isEmpty();
  }

  @Test
  void aDocumentOfMoreThanOneCustomerIsOnlyPossible() {
    var stayId = arrival("opera-R3", "Marta Soler");
    mdm.ambiguousDocument(documentOf(stayId, "Marta Soler", false).documentNumber());

    kardex.scanned(stayId, 1);

    var view = recognition.view(stayId, 1);
    assertThat(view.certainty()).isEqualTo(Certainty.POSSIBLE);
    assertThat(view.candidates()).isEmpty();
    assertThat(view.history()).isEmpty();
  }

  @Test
  void anUnknownDocumentLooksForCandidatesByNameAndBirthDate() {
    var withCandidates = arrival("opera-R4", "Lucía Gómez");
    var doc = documentOf(withCandidates, "Lucía Gómez", false);
    mdm.candidate("Gómez", new Candidate("C-R4", "ACTIVE", "Lucía", "Gómez", doc.birthDate(), "ES", List.of("NAME", "BIRTH_DATE")));

    kardex.scanned(withCandidates, 1);

    var view = recognition.view(withCandidates, 1);
    assertThat(view.certainty()).isEqualTo(Certainty.POSSIBLE);
    assertThat(view.matchedBy()).isEqualTo(MatchedBy.CANDIDATE);
    assertThat(view.candidates()).extracting(c -> c.customerId()).containsExactly("C-R4");

    var withoutCandidates = arrival("opera-R5", "Iván Núñez");
    kardex.scanned(withoutCandidates, 1);
    assertThat(recognition.view(withoutCandidates, 1).certainty()).isNull();
    assertThat(recognition.hasNews(withoutCandidates, 1)).isFalse();
  }

  @Test
  void anMdmThatDoesNotAnswerRecognisesNobodyAndTheScanGoesOn() {
    var stayId = arrival("opera-R6", "Marta Soler");
    mdm.down = true;

    var scanned = kardex.scanned(stayId, 1);

    assertThat(scanned.documentNumber()).isNotBlank();
    assertThat(guests.findById("opera-R6").orElseThrow().identityComplete()).isTrue();
    assertThat(recognition.view(stayId, 1).certainty()).isNull();
    assertThat(sent()).anyMatch(p -> p.contains("\"documentNumber\":\"" + scanned.documentNumber() + "\""));
  }

  @Test
  void aNewPassportConfirmedByRiuClassIsAKnownCustomerAndGoesToTheMdmAsTheirs() {
    var stayId = arrival("opera-R7", "Carlos Vidal");
    var usual = documentOf(stayId, "Carlos Vidal", false);
    var passport = documentOf(stayId, "Carlos Vidal", true);
    assertThat(passport.documentNumber()).isNotEqualTo(usual.documentNumber());
    assertThat(passport.birthDate()).isEqualTo(usual.birthDate());
    var customer = new Customer("C-R7", "ACTIVE", "Carlos", "Vidal", usual.birthDate());
    mdm.document(usual.documentNumber(), customer);
    mdm.candidate("Vidal", new Candidate("C-R7", "ACTIVE", "Carlos", "Vidal", usual.birthDate(), "ES", List.of("NAME")));
    mdm.riuClass("RC12345678", customer);
    history.summaries.put("C-R7", fiveStays("C-R7"));

    var scanned = kardex.scannedNewPassport(stayId, 1);

    assertThat(scanned.documentNumber()).isEqualTo(passport.documentNumber());
    assertThat(scanned.documentType()).isEqualTo(DemoDocuments.PASSPORT);
    assertThat(recognition.view(stayId, 1).certainty()).isEqualTo(Certainty.POSSIBLE);
    assertThat(recognition.view(stayId, 1).history()).isEmpty();
    // the scan's own command, as always: no customer confirmed
    assertThat(sent()).filteredOn(p -> p.contains(passport.documentNumber()))
        .singleElement().asString().contains("\"confirmedCustomerId\":null");

    var answer = recognition.confirm(stayId, 1, "ana", "12345678");

    assertThat(answer.done()).isTrue();
    var view = recognition.view(stayId, 1);
    assertThat(view.certainty()).isEqualTo(Certainty.KNOWN);
    assertThat(view.matchedBy()).isEqualTo(MatchedBy.RIU_CLASS);
    assertThat(view.stays()).isPresent();
    // the new passport again, saying whose it is: the MDM adds it to C-R7
    assertThat(sent()).filteredOn(p -> p.contains(passport.documentNumber()) && p.contains("\"confirmedCustomerId\":\"C-R7\""))
        .singleElement().asString().contains("\"type\":\"record-scanned-identity\"").contains("\"documentType\":\"PASSPORT\"")
        .contains("\"issuingCountry\":");
  }

  @Test
  void aConfirmationTheMdmDoesNotKnowChangesNothing() {
    var stayId = arrival("opera-R8", "Carlos Vidal");

    assertThat(recognition.confirm(stayId, 1, "ana", "nadie@example.com").done()).isFalse();
    assertThat(recognition.confirm(stayId, 1, "ana", "hola").done()).isFalse();
    assertThat(recognition.view(stayId, 1).certainty()).isNull();
  }

  @Test
  void aSearchByNameAndBirthDateIsOnlyPossible() {
    var stayId = arrival("opera-R9", "Sin Documento");
    mdm.candidate("Pons", new Candidate("C-R9", "ACTIVE", "Leo", "Pons", BORN, "ES", List.of("NAME", "BIRTH_DATE")));

    assertThat(recognition.searchByName(stayId, 1, "Leo", "Pons", null).done()).isFalse();
    assertThat(recognition.searchByName(stayId, 1, "Leo", "Pons", BORN).done()).isTrue();

    assertThat(recognition.view(stayId, 1).certainty()).isEqualTo(Certainty.POSSIBLE);
    assertThat(recognition.view(stayId, 1).history()).isEmpty();
  }

  @Test
  void aGuestTheReservationNamesByTheChainsCodeIsKnownWithoutAScan() {
    var stayId = arrival("C-R10", "Elena Mas");
    history.summaries.put("C-R10", fiveStays("C-R10"));

    var view = recognition.view(stayId, 1);

    assertThat(view.certainty()).isEqualTo(Certainty.KNOWN);
    assertThat(view.matchedBy()).isEqualTo(MatchedBy.CHAIN_CODE);
    assertThat(view.customerId()).isEqualTo("C-R10");
    assertThat(view.stays()).isPresent();
  }

  @Autowired DemoKnownCustomers known;

  /** The demo's seeding: the holder's scanned document, a Riu Class number, past stays, a membership — the same every time. */
  @Test
  void theDemosKnownCustomersAreSeededWithTheDocumentTheScannerReads() {
    var stayId = arrival("C-R11", "Nuria Ferrer");
    var doc = documentOf(stayId, "Nuria Ferrer", false);

    var seeded = known.seed(50).stream().filter(s -> s.stayId().equals(stayId)).toList();
    var again = known.seed(50).stream().filter(s -> s.stayId().equals(stayId)).toList();

    assertThat(seeded).singleElement().satisfies(s -> {
      assertThat(s.customerId()).isEqualTo("C-R11");
      assertThat(s.documentNumber()).isEqualTo(doc.documentNumber());
      assertThat(s.riuClass()).matches("RC\\d{8}");
    });
    assertThat(again).isEqualTo(seeded);
    var member = seeded.getFirst().riuClass();
    assertThat(mdm.writes).contains("riuClass C-R11 " + member)
        .anyMatch(w -> w.startsWith("document C-R11 " + doc.documentType() + " " + doc.documentNumber()));
    assertThat(history.seeded.get("C-R11")).isBetween(4, 6);
    var membership = loyalty.of("C-R11", member).orElseThrow();
    var guest = guests.findById("C-R11").orElseThrow();
    assertThat(guest.tier().name()).isEqualTo(membership.tier()).isIn("GOLD", "PLATINUM");
    assertThat(guest.loyaltyPoints()).isEqualTo(membership.points());
  }

  List<String> sent() {
    return outbox.all(CommandOutbox.CUSTOMER_COMMANDS).stream().map(OutboxMessage::payload).toList();
  }
}
