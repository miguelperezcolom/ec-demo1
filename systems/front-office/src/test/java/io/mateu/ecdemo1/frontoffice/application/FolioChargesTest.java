package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.domain.folio.ChargeKind;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioLine;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import io.mateu.ecdemo1.frontoffice.infra.pms.ChargePostings;
import io.mateu.ecdemo1.frontoffice.infra.pms.StayInvoices;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The desk's charges go up to the PMS's folio (pms-fo): each charge of the desk — an extra of the
 * check-in, the late check-out, a consumption — is an event in the outbox with the charge, naming its
 * folio line; the accommodation is not (the PMS charges it itself); a void is one too, and the line
 * stays on the folio counting for nothing. The PMS's answer is kept per line, and the proforma says
 * both totals.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:folio-charges;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class FolioChargesTest {

  @Autowired CheckInService checkIn;
  @Autowired CheckOutService checkOut;
  @Autowired FolioService folioService;
  @Autowired PmsStays pms;
  @Autowired Invoices invoices;
  @Autowired StayInvoices stayInvoices;
  @Autowired ChargePostings postings;
  @Autowired GuestRepository guests;
  @Autowired StayRepository stays;
  @Autowired RoomRepository rooms;
  @Autowired FolioRepository folios;
  @Autowired CommandOutbox outbox;

  List<io.mateu.ecdemo1.messaging.OutboxMessage> events(String stayId) {
    return outbox.all(CommandOutbox.FRONT_OFFICE_EVENTS).stream().filter(e -> e.key().equals("MRU01/" + stayId)).toList();
  }

  @Test
  void theDesksChargesGoUpToThePmsFolioButNotTheAccommodation() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);

    checkIn.checkIn(a.stayId(), null, List.of("transfer"));
    folioService.contractLateCheckOut(a.stayId(), "ana");
    folioService.postCharge(a.stayId(), "MB-02", "ana");

    var folio = folios.findByStayId(a.stayId()).orElseThrow();
    assertThat(folio.lines()).extracting(FolioLine::kind).containsExactly(ChargeKind.ACCOMMODATION, ChargeKind.ADD_ON,
        ChargeKind.LATE_CHECK_OUT, ChargeKind.CONSUMPTION);
    assertThat(folio.lines()).allSatisfy(l -> assertThat(l.id()).matches("L-[0-9A-F]{8}"));
    // The check-in first, then each charge after it — in the order the PMS must take them.
    assertThat(events(a.stayId())).extracting(io.mateu.ecdemo1.messaging.OutboxMessage::type)
        .containsExactly("GuestCheckedIn", "ChargePosted", "ChargePosted", "ChargePosted");
    var charges = events(a.stayId()).stream().skip(1).map(io.mateu.ecdemo1.messaging.OutboxMessage::payload).toList();
    assertThat(charges.get(0)).contains("\"kind\":\"ADD_ON\"", "\"code\":\"transfer\"", "\"lineId\":\"" + folio.lines().get(1).id() + "\"");
    assertThat(charges.get(1)).contains("\"kind\":\"LATE_CHECK_OUT\"", "\"amount\":50.00", "\"by\":\"ana\"");
    assertThat(charges.get(2)).contains("\"kind\":\"CONSUMPTION\"", "\"code\":\"MB-02\"", "\"description\":\"Minibar\"");
    assertThat(charges).noneMatch(p -> p.contains("Alojamiento"));
    // Until the PMS answers, each says it is on its way.
    assertThat(postings.of(folio.lines().get(2).id()).orElseThrow().state()).isEqualTo("Opera: pendiente — cargo enviado");
    assertThat(postings.of(folio.lines().get(0).id())).isEmpty();
  }

  @Test
  void aVoidTakesTheLineBackHereAndInThePms() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    checkIn.checkIn(a.stayId(), null, List.of());
    folioService.postCharge(a.stayId(), "MB-02", "ana");
    var folio = folios.findByStayId(a.stayId()).orElseThrow();
    var minibar = folio.lines().getLast();
    var before = folio.balance();

    var voided = folioService.voidCharge(a.stayId(), minibar.id(), "ana");

    assertThat(voided).get().extracting(FolioLine::voided).isEqualTo(true);
    var after = folios.findByStayId(a.stayId()).orElseThrow();
    assertThat(after.lines()).hasSize(folio.lines().size());
    assertThat(after.balance()).isEqualByComparingTo(before.subtract(minibar.amount()));
    assertThat(events(a.stayId())).extracting(io.mateu.ecdemo1.messaging.OutboxMessage::type)
        .containsExactly("GuestCheckedIn", "ChargePosted", "ChargeVoided");
    assertThat(events(a.stayId()).getLast().payload()).contains("\"lineId\":\"" + minibar.id() + "\"", "\"amount\":12.50");

    // Voided again: nothing more. The accommodation is the PMS's: not the desk's to void.
    assertThat(folioService.voidCharge(a.stayId(), minibar.id(), "ana")).isPresent();
    assertThat(folioService.voidCharge(a.stayId(), after.lines().getFirst().id(), "ana")).isEmpty();
    assertThat(events(a.stayId())).hasSize(3);
    // Voided, the late check-out can be contracted again.
    folioService.contractLateCheckOut(a.stayId(), "ana");
    var late = folios.findByStayId(a.stayId()).orElseThrow().lines().getLast();
    folioService.voidCharge(a.stayId(), late.id(), "ana");
    assertThat(folios.findByStayId(a.stayId()).orElseThrow().lateCheckOutContracted()).isFalse();
    assertThat(folioService.contractLateCheckOut(a.stayId(), "ana")).isTrue();
  }

  @Test
  void thePmsAnswerIsKeptPerLine() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    checkIn.checkIn(a.stayId(), null, List.of());
    folioService.postCharge(a.stayId(), "RS-01", "ana");
    var line = folios.findByStayId(a.stayId()).orElseThrow().lines().getLast();

    pms.take(new FrontOfficeCommand.RecordCharge(UUID.randomUUID().toString(), "XMAR", "39486034", a.stayId(), line.id(),
        false, true, "Transaction code 1403 is not valid", null));
    assertThat(postings.of(line.id()).orElseThrow().refused()).isTrue();
    assertThat(postings.of(line.id()).orElseThrow().state()).isEqualTo(
        "Opera: rechazado (cargo) — Transaction code 1403 is not valid");

    pms.take(new FrontOfficeCommand.RecordCharge(UUID.randomUUID().toString(), "XMAR", "39486034", a.stayId(), line.id(),
        false, false, "En el folio de Opera", "88731245"));
    pms.take(new FrontOfficeCommand.RecordCharge(UUID.randomUUID().toString(), "XMAR", "39486034", a.stayId(), line.id(),
        true, false, "Anulado en el folio de Opera", "88731246"));
    var posting = postings.of(line.id()).orElseThrow();
    assertThat(posting.postingId()).isEqualTo("88731245");
    assertThat(posting.reversalId()).isEqualTo("88731246");
    assertThat(posting.state()).isEqualTo("Opera: anulado · 88731246");
    assertThat(postings.ofStay(a.stayId())).containsKey(line.id());
  }

  @Test
  void theProformaSaysBothTotalsAndWhetherTheyMatch() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    checkIn.checkIn(a.stayId(), null, List.of());
    folioService.contractLateCheckOut(a.stayId(), "ana");
    checkOut.checkOut(a.stayId());
    var total = folios.findByStayId(a.stayId()).orElseThrow().balance(); // 300 + 50

    stayInvoices.save(a.stayId(), new FrontOfficeCommand.Invoice("OPERA", "XMAR401", LocalDate.of(2026, 5, 13), total,
        "MUR", null), java.time.Instant.now());
    assertThat(invoices.summary(a.stayId()).label())
        .isEqualTo("Abrir factura proforma (front office) · Opera XMAR401 350,00 MUR, coincide");
    assertThat(Invoices.totals(total, stayInvoices.of(a.stayId()).orElseThrow())).singleElement().asString()
        .contains("coinciden");

    stayInvoices.save(a.stayId(), new FrontOfficeCommand.Invoice("OPERA", "XMAR401", LocalDate.of(2026, 5, 13),
        new BigDecimal("100.00"), "MUR", null), java.time.Instant.now());
    assertThat(invoices.summary(a.stayId()).label()).endsWith("front office 350.00");
    assertThat(String.join(" ", Invoices.totals(total, stayInvoices.of(a.stayId()).orElseThrow())))
        .contains("NO coinciden (diferencia 250.00)").contains("alojamiento");
    assertThat(new String(invoices.document(a.stayId()).orElseThrow().pdf(), 0, 5,
        java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
  }
}
