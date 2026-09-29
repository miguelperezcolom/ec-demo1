package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.application.PmsStays.Outcome;
import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.mdm.CustomerEvents;
import io.mateu.ecdemo1.frontoffice.infra.mdm.Kardex;
import io.mateu.ecdemo1.frontoffice.infra.pms.PmsCatalogue;
import io.mateu.ecdemo1.frontoffice.infra.pms.PmsLinks;
import io.mateu.ecdemo1.integration.model.customer.CustomerChanged;
import io.mateu.ecdemo1.integration.model.customer.GoldenRecord;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueEntry;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueType;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.PmsStatus;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.ReplaceCatalogue;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.WriteStay;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

/** The front office consumes the PMS: the stays as Opera holds them, read with Opera's catalogue. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:pms-stays;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "frontoffice.pms-hotel=XMAR"})
class PmsStaysTest {

  @Autowired PmsStays pms;
  @Autowired StayRepository stays;
  @Autowired GuestRepository guests;
  @Autowired WalkIns walkIns;
  @Autowired PmsCatalogue catalogue;
  @Autowired PmsLinks links;
  @Autowired StayWrites writes;
  @Autowired Kardex kardex;

  @BeforeEach
  void catalogue() {
    pms.take(new ReplaceCatalogue(id(), "XMAR", List.of(
        new CatalogueEntry(CatalogueType.ROOM_TYPE, "STDK", "Estándar King", null),
        new CatalogueEntry(CatalogueType.PACKAGE, "BRKFST", "Desayuno buffet", null),
        new CatalogueEntry(CatalogueType.RATE_PLAN, "406484DIRXM", "DIRECTOS XMU A26", null),
        new CatalogueEntry(CatalogueType.ROOM, "001", "Doble Baño Jardín Balcón", "DBJB"))));
  }

  static String id() {
    return UUID.randomUUID().toString();
  }

  static WriteStay stay(String rid, String locator, String version, PmsStatus status, String board, String customerId,
      List<String> refs) {
    return new WriteStay(id(), "XMAR", rid, "C" + rid, locator, refs, version, status,
        new FrontOfficeCommand.Person(customerId, "P" + rid, "Nora Moreau", null, "nora@example.com", null), List.of(),
        "STDK", "406484DIRXM", board, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), 2, "Central Reservation",
        new BigDecimal("372.00"), "EUR");
  }

  static WriteStay stay(String rid, String locator, String version) {
    return stay(rid, locator, version, PmsStatus.RESERVED, "BRKFST", "C-" + rid, List.of());
  }

  @Test
  void aReservationOfThePmsBecomesAStayInThePmsWords() {
    assertThat(pms.take(stay("R1", "LOC1", "2026-09-27T10:00:00"))).isEqualTo(Outcome.WRITTEN);
    var s = stays.findById("LOC1").orElseThrow();
    assertThat(s.roomType()).isEqualTo("Estándar King");
    assertThat(s.board()).isEqualTo("Desayuno buffet");
    assertThat(s.status()).isEqualTo(StayStatus.ARRIVING);
    assertThat(s.guestId()).isEqualTo("C-R1");
    assertThat(links.ofStay("LOC1").orElseThrow().pmsReservationId()).isEqualTo("R1");

    // Born in Opera: no CRS locator, no customer — the confirmation and the Opera profile.
    pms.take(stay("R2", null, "2026-09-27T10:00:00", PmsStatus.RESERVED, null, null, List.of()));
    var born = stays.findById("OP-CR2").orElseThrow();
    assertThat(born.board()).isEqualTo(PmsStays.ROOM_ONLY);
    assertThat(born.guestId()).isEqualTo("opera-PR2");

    // A code the catalogue does not have is shown as it is.
    var unknown = stay("R3", "LOC3", "2026-09-27T10:00:00");
    pms.take(new WriteStay(id(), "XMAR", "R3", "CR3", "LOC3", List.of(), unknown.pmsVersion(), PmsStatus.RESERVED,
        unknown.holder(), List.of(), "SUPE", null, "BKF", unknown.checkIn(), unknown.checkOut(), 2, "x", null, "EUR"));
    assertThat(stays.findById("LOC3").orElseThrow().roomType()).isEqualTo("SUPE");
  }

  @Test
  void aStayTheCrsAlreadyWroteIsFoundByItsLocatorThenByTheOperaId() {
    writes.write("LOC10", new StayWrites.Booking("C-OLD", new StayWrites.Holder("Nora Moreau", null, null, null),
        List.of(new Companion("pax-2", "Leo Moreau", "Pax 2 · de la reserva")), "Standard King", "Desayuno",
        LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), 2, "Directo · WEB", null), false);
    var before = stays.findAll().size();

    pms.take(stay("R10", "LOC10", "2026-09-27T10:00:00"));
    assertThat(stays.findAll()).hasSize(before);
    var s = stays.findById("LOC10").orElseThrow();
    assertThat(s.roomType()).isEqualTo("Estándar King");
    // Opera keeps no companions: the CRS's stay on.
    assertThat(s.companions()).extracting(Companion::name).containsExactly("Leo Moreau");

    // The same reservation, now found by Opera's id even without the locator.
    pms.take(new WriteStay(id(), "XMAR", "R10", "CR10", null, List.of(), "2026-09-27T11:00:00", PmsStatus.RESERVED,
        stay("R10", null, "x").holder(), List.of(), "STDK", null, null, LocalDate.of(2026, 11, 10),
        LocalDate.of(2026, 11, 14), 2, "x", null, "EUR"));
    assertThat(stays.findAll()).hasSize(before);
    assertThat(stays.findById("LOC10").orElseThrow().checkOut()).isEqualTo(LocalDate.of(2026, 11, 14));
  }

  @Test
  void idempotentAndOrderedByThePmsVersion() {
    var first = stay("R20", "LOC20", "2026-09-27T10:00:00");
    assertThat(pms.take(first)).isEqualTo(Outcome.WRITTEN);
    assertThat(pms.take(first)).isEqualTo(Outcome.DUPLICATE);

    // The same version again, told anew (a merge in the MDM): written again, the new customer on it.
    var sameVersion = stay("R20", "LOC20", "2026-09-27T10:00:00", PmsStatus.RESERVED, "BRKFST", "C-SURVIVOR", List.of());
    assertThat(pms.take(sameVersion)).isEqualTo(Outcome.WRITTEN);
    assertThat(stays.findById("LOC20").orElseThrow().guestId()).isEqualTo("C-SURVIVOR");

    var later = stay("R20", "LOC20", "2026-09-27T12:00:00", PmsStatus.RESERVED, null, "C-SURVIVOR", List.of());
    pms.take(later);
    var older = stay("R20", "LOC20", "2026-09-27T11:00:00", PmsStatus.RESERVED, "BRKFST", "C-SURVIVOR", List.of());
    assertThat(pms.take(older)).isEqualTo(Outcome.STALE);
    assertThat(stays.findById("LOC20").orElseThrow().board()).isEqualTo(PmsStays.ROOM_ONLY);
    assertThat(links.ofStay("LOC20").orElseThrow().pmsVersion()).isEqualTo("2026-09-27T12:00:00");
  }

  @Test
  void aWalkInComesBackOntoItsOwnStay() {
    // Booked in the CRS: the walk-in knows the locator the CRS gave it.
    guests.save(Guest.fromReservation("wi-FO-WI1", "Nora Moreau", null, null, null));
    stays.save(Stay.fromReservation("FO-WI1", "wi-FO-WI1", "Estándar King", PmsStays.ROOM_ONLY, LocalDate.of(2026, 11, 10),
        LocalDate.of(2026, 11, 11), 1, "Walk-in", null, List.of()));
    walkIns.save(WalkIn.pending("FO-WI1", "{}", null, Instant.now()).booked("LOCWI1", Instant.now()));
    pms.take(stay("R30", "LOCWI1", "2026-09-27T10:00:00"));
    assertThat(stays.findById("LOCWI1")).isEmpty();
    assertThat(stays.findById("FO-WI1").orElseThrow().guestId()).isEqualTo("C-R30");
    assertThat(walkIns.of("FO-WI1").orElseThrow().pmsReservationId()).isEqualTo("R30");

    // Not answered yet by the CRS: Opera carries the stay's id as a reference.
    guests.save(Guest.fromReservation("wi-FO-WI2", "Leo Pons", null, null, null));
    stays.save(Stay.fromReservation("FO-WI2", "wi-FO-WI2", "Estándar King", PmsStays.ROOM_ONLY, LocalDate.of(2026, 11, 10),
        LocalDate.of(2026, 11, 11), 1, "Walk-in", null, List.of()));
    walkIns.save(WalkIn.pending("FO-WI2", "{}", null, Instant.now()));
    pms.take(stay("R31", "LOCWI2", "2026-09-27T10:00:00", PmsStatus.RESERVED, null, "C-R31", List.of("FO-WI2")));
    assertThat(stays.findById("LOCWI2")).isEmpty();
    assertThat(stays.findById("FO-WI2").orElseThrow().guestId()).isEqualTo("C-R31");
    assertThat(walkIns.of("FO-WI2").orElseThrow().locator()).isEqualTo("LOCWI2");
  }

  @Test
  void cancellationsAndNoShows() {
    pms.take(stay("R40", "LOC40", "2026-09-27T10:00:00"));
    assertThat(pms.take(stay("R40", "LOC40", "2026-09-27T11:00:00", PmsStatus.CANCELLED, null, "C-R40", List.of())))
        .isEqualTo(Outcome.CANCELLED);
    assertThat(stays.findById("LOC40").orElseThrow().status()).isEqualTo(StayStatus.CANCELLED);

    // In the house already: the desk's.
    pms.take(stay("R41", "LOC41", "2026-09-27T10:00:00"));
    stays.save(stays.findById("LOC41").orElseThrow().assignRoom("1204", "Estándar King").completeCheckIn());
    assertThat(pms.take(stay("R41", "LOC41", "2026-09-27T11:00:00", PmsStatus.CANCELLED, null, "C-R41", List.of())))
        .isEqualTo(Outcome.KEPT_BY_THE_DESK);
    assertThat(stays.findById("LOC41").orElseThrow().status()).isEqualTo(StayStatus.IN_HOUSE);

    // Never here: nothing is created for it.
    assertThat(pms.take(stay("R42", "LOC42", "2026-09-27T11:00:00", PmsStatus.CANCELLED, null, "C-R42", List.of())))
        .isEqualTo(Outcome.UNKNOWN_STAY);
    assertThat(stays.findById("LOC42")).isEmpty();

    // A no-show costs its fee.
    pms.take(stay("R43", "LOC43", "2026-09-27T10:00:00"));
    var noShow = stay("R43", "LOC43", "2026-09-27T11:00:00", PmsStatus.NO_SHOW, null, "C-R43", List.of());
    pms.take(new WriteStay(id(), "XMAR", "R43", "CR43", "LOC43", List.of(), noShow.pmsVersion(), PmsStatus.NO_SHOW,
        noShow.holder(), List.of(), "STDK", null, null, noShow.checkIn(), noShow.checkOut(), 2, "x",
        new BigDecimal("139.50"), "EUR"));
    var s = stays.findById("LOC43").orElseThrow();
    assertThat(s.status()).isEqualTo(StayStatus.NO_SHOW);
    assertThat(s.total()).isEqualByComparingTo("139.50");
  }

  @Autowired io.mateu.ecdemo1.frontoffice.infra.pms.StayInvoices invoices;
  @Autowired Invoices invoiceDocuments;

  static FrontOfficeCommand.RecordReception reception(String rid, String stayId, FrontOfficeCommand.ReceptionOperation op,
      boolean refused, String detail, String room, FrontOfficeCommand.Invoice invoice) {
    return new FrontOfficeCommand.RecordReception(id(), "XMAR", rid, stayId, op, refused, detail, room, invoice);
  }

  @Test
  void thePmsSaysHowItTookTheReception() {
    pms.take(stay("R60", "LOC60", "2026-09-27T10:00:00"));
    stays.save(stays.findById("LOC60").orElseThrow().assignRoom("1204", "Estándar King").completeCheckIn());

    // Refused: the stay says why.
    assertThat(pms.take(reception("R60", "LOC60", FrontOfficeCommand.ReceptionOperation.CHECK_IN, true,
        "The guest's arrival is not scheduled for today. Check-in not possible.", null, null))).isEqualTo(Outcome.RECEPTION);
    assertThat(links.stateOf("LOC60")).contains(
        "Opera: rechazado (check-in) — The guest's arrival is not scheduled for today. Check-in not possible.");

    // Done: the room Opera has them in is the stay's.
    pms.take(reception("R60", "LOC60", FrontOfficeCommand.ReceptionOperation.CHECK_IN, false, "En casa en Opera", "205", null));
    assertThat(links.stateOf("LOC60")).contains("Opera: en casa · hab. 205");
    assertThat(stays.findById("LOC60").orElseThrow().roomNumber()).isEqualTo("205");

    // The projection brings the in-house state back: the desk's stay stays in the house, and says so.
    assertThat(pms.take(stay("R60", "LOC60", "2026-09-27T12:00:00", PmsStatus.IN_HOUSE, "BRKFST", "C-R60", List.of())))
        .isEqualTo(Outcome.WRITTEN);
    assertThat(stays.findById("LOC60").orElseThrow().status()).isEqualTo(StayStatus.IN_HOUSE);
    assertThat(links.stateOf("LOC60")).contains("Opera: en casa · hab. 205");
  }

  @Autowired RoomChangeService roomChange;

  @Test
  void aRoomOfThePmsIsGivenToAStayStillToArrive() {
    pms.take(stay("R62", "LOC62", "2026-09-27T10:00:00"));

    var room = roomChange.changeRoom("LOC62", "001");

    assertThat(room).isPresent();
    assertThat(stays.findById("LOC62").orElseThrow().roomNumber()).isEqualTo("001");
    assertThat(stays.findById("LOC62").orElseThrow().roomType()).isEqualTo("DBJB");
    assertThat(roomChange.changeRoom("LOC62", "not-a-room")).isEmpty();

    // In at the desk, but Opera refused the check-in: another room of the PMS, and the check-in goes up again.
    stays.save(stays.findById("LOC62").orElseThrow().completeCheckIn());
    links.state("LOC62", "Opera: rechazado (check-in) — Room 001 at property XMAR is Clean (CL).");
    assertThat(roomChange.changeRoom("LOC62", "001")).isPresent();
    assertThat(links.stateOf("LOC62")).contains("Opera: pendiente — check-in enviado");
  }

  @Test
  void theCheckOutsInvoiceIsThePmssDocumentOrTheFrontOfficesProforma() {
    pms.take(stay("R61", "LOC61", "2026-09-27T10:00:00"));
    stays.save(stays.findById("LOC61").orElseThrow().assignRoom("1205", "Estándar King").completeCheckIn().completeCheckOut());

    // No invoice from Opera yet: the proforma, labelled as such.
    assertThat(invoiceDocuments.summary("LOC61").fromThePms()).isFalse();
    assertThat(invoiceDocuments.summary("LOC61").label()).isEqualTo("Abrir factura proforma (front office)");
    var proforma = invoiceDocuments.document("LOC61").orElseThrow();
    assertThat(proforma.fromThePms()).isFalse();
    assertThat(new String(proforma.pdf(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

    // Opera's, with its document.
    var pdf = "%PDF-1.4 Opera folio".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    pms.take(reception("R61", "LOC61", FrontOfficeCommand.ReceptionOperation.CHECK_OUT, false, "Salida registrada en Opera",
        null, new FrontOfficeCommand.Invoice("OPERA", "XMAR377", LocalDate.of(2026, 5, 13), new BigDecimal("150.00"), "MUR",
            java.util.Base64.getEncoder().encodeToString(pdf))));
    assertThat(links.stateOf("LOC61")).contains("Opera: salida registrada · factura XMAR377");
    assertThat(invoices.of("LOC61").orElseThrow().number()).isEqualTo("XMAR377");
    assertThat(invoiceDocuments.summary("LOC61").label()).isEqualTo("Abrir factura · Opera XMAR377");
    assertThat(invoiceDocuments.document("LOC61").orElseThrow().pdf()).isEqualTo(pdf);

    // The link the desk opens it with is signed, and only for this stay.
    var link = invoiceDocuments.link("LOC61");
    var e = Long.parseLong(link.replaceAll(".*[?&]e=(\\d+).*", "$1"));
    var sig = link.replaceAll(".*[?&]s=([^&]+).*", "$1");
    assertThat(invoiceDocuments.valid("LOC61", e, sig)).isTrue();
    assertThat(invoiceDocuments.valid("LOC60", e, sig)).isFalse();
    assertThat(invoiceDocuments.valid("LOC61", 1, sig)).isFalse();
  }

  @Test
  void anotherPropertysCommandsAreNotThisFrontOffices() {
    var other = stay("R50", "LOC50", "2026-09-27T10:00:00");
    assertThat(pms.take(new WriteStay(id(), "XMU", "R50", "C", "LOC50", List.of(), other.pmsVersion(), PmsStatus.RESERVED,
        other.holder(), List.of(), "STDK", null, null, other.checkIn(), other.checkOut(), 2, "x", null, "EUR")))
        .isEqualTo(Outcome.OTHER_HOTEL);
    assertThat(stays.findById("LOC50")).isEmpty();
  }

  @Test
  void theCatalogueIsReplacedWholeAndSummarised() {
    var summary = catalogue.summary("XMAR");
    assertThat(summary.pmsHotelCode()).isEqualTo("XMAR");
    assertThat(summary.counts()).containsEntry("ROOM_TYPE", 1).containsEntry("ROOM", 1).containsEntry("PACKAGE", 1);
    pms.take(new ReplaceCatalogue("CAT-2", "XMAR", List.of(new CatalogueEntry(CatalogueType.ROOM_TYPE, "SUPE", "Superior", null))));
    summary = catalogue.summary("XMAR");
    assertThat(summary.commandId()).isEqualTo("CAT-2");
    assertThat(summary.counts()).containsOnlyKeys("ROOM_TYPE");
    assertThat(catalogue.summary("XMU").counts()).isEmpty();
  }

  @Test
  void theCommandsReadAsTheIntegrationWritesThem() {
    var json = """
        {"type":"write-stay","commandId":"X1","pmsHotelCode":"XMAR","pmsReservationId":"R60","confirmationNumber":"C60",
         "crsLocator":"LOC60","externalReferences":[],"pmsVersion":"2026-09-27T10:00:00","status":"RESERVED",
         "holder":{"customerId":"C-60","pmsProfileId":"P60","name":"Ana","document":null,"email":null,"phone":null},
         "companions":[],"roomTypeCode":"STDK","ratePlanCode":null,"boardCode":null,"checkIn":"2026-11-10",
         "checkOut":"2026-11-12","pax":2,"agency":"x","total":10.5,"currency":"EUR","unknown":1}""";
    var command = JsonMapper.builder().disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build().readValue(json, FrontOfficeCommand.class);
    assertThat(pms.take(command)).isEqualTo(Outcome.WRITTEN);
    assertThat(stays.findById("LOC60").orElseThrow().roomType()).isEqualTo("Estándar King");
  }

  @Test
  void theMdmsCustomerEventsReachTheKardex() {
    guests.save(Guest.fromReservation("C-K1", "Ana García", null, "ana@example.com", null));
    var event = new CustomerChanged("E1", Instant.now(), "C-K1", 2,
        new GoldenRecord("Ana", "García López", "ana.gl@example.com", null, null, null, null, "12345678Z"), true, null,
        null, null, List.of("MRU01/LOC1"));
    assertThat(CustomerEvents.apply(kardex, event)).isTrue();
    assertThat(guests.findById("C-K1").orElseThrow().email()).isEqualTo("ana.gl@example.com");
    assertThat(CustomerEvents.apply(kardex, new CustomerChanged("E2", Instant.now(), "C-NOBODY", 1,
        event.data(), true, null, null, null, List.of()))).isFalse();
  }
}
