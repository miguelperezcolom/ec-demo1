package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.domain.customer.ArrivalBriefings;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions.Certainty;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions.MatchedBy;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory.HistorySummary;
import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The day's arrivals briefed before they arrive: the returning customers among them, with their stays
 * and their Riu Class standing, kept — what the arrivals list shows and what the check-in uses without
 * asking customer-history or loyalty then.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:briefing;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "frontoffice.arrivals-briefing.enabled=false"})
@Import(RecognitionTest.Doubles.class)
class ArrivalsBriefingTest {

  static final AtomicInteger SEQ = new AtomicInteger();

  @Autowired InMemoryCustomers.Directory mdm;
  @Autowired InMemoryCustomers.History history;
  @Autowired InMemoryCustomers.RiuClass loyalty;
  @Autowired ArrivalsBriefing briefing;
  @Autowired ArrivalBriefings briefings;
  @Autowired Recognition recognition;
  @Autowired GuestRepository guests;
  @Autowired StayRepository stays;

  String arriving(String guestId, String name, LocalDate on) {
    guests.save(Guest.fromReservation(guestId, name, null, null, null));
    var stayId = "BRF-" + SEQ.incrementAndGet();
    stays.save(Stay.fromReservation(stayId, guestId, "Doble", "Desayuno", on, on.plusDays(3), 1, "TUI",
        new BigDecimal("300.00"), List.of()));
    return stayId;
  }

  static HistorySummary stays(String code, int n) {
    return new HistorySummary(code, n, n * 4, LocalDate.of(2020, 6, 1), LocalDate.of(2025, 9, 1), List.of(), 2, "MRU01",
        new BigDecimal("500.00"), "EUR");
  }

  @Test
  void aReturningCustomerArrivingTodayIsBriefedWithTheirStaysAndTier() {
    var stayId = arriving("C-BRF1", "Elena Puig", LocalDate.now());
    history.summaries.put("C-BRF1", stays("C-BRF1", 5));
    loyalty.enroll("RC00000901", "C-BRF1", "GOLD", 15000, LocalDate.of(2020, 1, 1));

    briefing.prepare();

    assertThat(briefings.of(stayId, 1)).get().satisfies(b -> {
      assertThat(b.customerId()).isEqualTo("C-BRF1");
      assertThat(b.customerName()).isEqualTo("Elena Puig");
      assertThat(b.history().stays()).isEqualTo(5);
      assertThat(b.loyalty().tier()).isEqualTo("GOLD");
      assertThat(ArrivalsBriefing.line(b)).isEqualTo("Repite · 5 estancias · GOLD");
    });
    assertThat(briefing.holders()).extracting(ArrivalBriefings.Briefing::stayId).contains(stayId);
  }

  @Test
  void theCheckInUsesTheBriefing_withoutAskingTheServicesAgain() {
    var stayId = arriving("C-BRF2", "Pau Ferrer", LocalDate.now());
    history.summaries.put("C-BRF2", stays("C-BRF2", 3));
    briefing.prepare();
    // customer-history does not answer at the desk any more: the briefing is enough
    history.summaries.remove("C-BRF2");

    var view = recognition.view(stayId, 1);

    assertThat(view.certainty()).isEqualTo(Certainty.KNOWN);
    assertThat(view.matchedBy()).isEqualTo(MatchedBy.CHAIN_CODE);
    assertThat(view.stays()).get().extracting(HistorySummary::stays).isEqualTo(3);
  }

  @Test
  void aReservationFromOperaIsBriefedByTheCustomerItsProfileIs() {
    var stayId = arriving("opera-555001", "Nora Pons", LocalDate.now());
    mdm.operaProfiles.put("555001", "C-BRF8");
    history.summaries.put("C-BRF8", stays("C-BRF8", 2));
    loyalty.enroll("RC00000908", "C-BRF8", "PLATINUM", 42000, LocalDate.of(2015, 1, 1));

    briefing.prepare();

    assertThat(briefings.of(stayId, 1)).get().satisfies(b -> {
      assertThat(b.customerId()).isEqualTo("C-BRF8");
      assertThat(ArrivalsBriefing.line(b)).isEqualTo("Repite · 2 estancias · PLATINUM");
    });
    var view = recognition.view(stayId, 1);
    assertThat(view.certainty()).isEqualTo(Certainty.KNOWN);
    assertThat(view.customerId()).isEqualTo("C-BRF8");
  }

  @Test
  void aProvisionalCodeWithNoStays_andAnArrivalLaterThanTomorrow_areNotBriefed() {
    var provisional = arriving("C-BRF3", "Nadie Nuevo", LocalDate.now());
    history.summaries.put("C-BRF3", stays("C-BRF3", 0));
    var later = arriving("C-BRF4", "Luis Vich", LocalDate.now().plusDays(3));
    history.summaries.put("C-BRF4", stays("C-BRF4", 2));
    var tomorrow = arriving("C-BRF5", "Rosa Gil", LocalDate.now().plusDays(1));
    history.summaries.put("C-BRF5", stays("C-BRF5", 1));
    var notChain = arriving("opera-BRF6", "Ana Mas", LocalDate.now());

    briefing.prepare();

    assertThat(briefings.of(provisional, 1)).isEmpty();
    assertThat(briefings.of(later, 1)).isEmpty();
    assertThat(briefings.of(notChain, 1)).isEmpty();
    assertThat(briefings.of(tomorrow, 1)).get().satisfies(b ->
        assertThat(ArrivalsBriefing.line(b)).isEqualTo("Repite · 1 estancia"));
  }

  @Test
  void aServiceThatDoesNotAnswerKeepsTheBriefing_aStayNoLongerArrivingLosesIt() {
    var stayId = arriving("C-BRF7", "Marc Roig", LocalDate.now());
    history.summaries.put("C-BRF7", stays("C-BRF7", 4));
    briefing.prepare();

    history.summaries.remove("C-BRF7");
    briefing.prepare();
    assertThat(briefings.of(stayId, 1)).isPresent();

    stays.save(stays.findById(stayId).orElseThrow().cancel());
    briefing.prepare();
    assertThat(briefings.of(stayId, 1)).isEmpty();
  }
}
