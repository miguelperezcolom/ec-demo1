package io.mateu.ecdemo1.frontoffice.infra.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.room.HousekeepingStatus;
import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomOccupancy;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.application.CheckInService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The reception agent's tools against the real use cases (H2): what they read, and that nothing they
 * prepare is done until the person confirms it — in a later message — and then audited with the agent
 * as the actor.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mcp-tools;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@Import(FrontDeskMcpToolsTest.Caller.class)
class FrontDeskMcpToolsTest {

  /** The person chatting and the turn (MCP session) the call came in on, as the test says. */
  static class FakeCaller implements McpCaller {
    String person = "ana";
    String turn = "turn-1";

    @Override
    public String person() {
      return person;
    }

    @Override
    public String turn() {
      return turn;
    }
  }

  /** Imported, not scanned: another test's context must keep the real caller. */
  static class Caller {
    @Bean
    @Primary
    FakeCaller fakeCaller() {
      return new FakeCaller();
    }
  }

  static final AtomicInteger SEQ = new AtomicInteger();
  static final Pattern TOKEN = Pattern.compile("token ([A-Z0-9]{6})");

  @Autowired FrontDeskMcpTools tools;
  @Autowired FakeCaller caller;
  @Autowired StayRepository stays;
  @Autowired GuestRepository guests;
  @Autowired RoomRepository rooms;
  @Autowired FolioRepository folios;
  @Autowired WalkIns walkIns;
  @Autowired CheckInService checkIn;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void aNewConversation() {
    caller.person = "ana";
    caller.turn = "turn-" + SEQ.incrementAndGet();
  }

  // ── lecturas ─────────────────────────────────────────────────────────────────

  @Test
  void todaysArrivalsSayWhoIsStillToIdentify() {
    var stayId = arrival(2);

    var arrival = tools.listArrivals().stream().filter(s -> s.stayId().equals(stayId)).findFirst().orElseThrow();

    assertThat(arrival.status()).isEqualTo("ARRIVING");
    assertThat(arrival.pax()).isEqualTo(2);
    assertThat(arrival.paxPendingIdentity()).isEqualTo(2);
    assertThat(arrival.readyForCheckIn()).isFalse();
    assertThat(arrival.crsLocator()).isEqualTo(stayId);
  }

  @Test
  void aTomorrowsArrivalIsNotToday() {
    var n = SEQ.incrementAndGet();
    guests.save(Guest.fromReservation("C-MCP-T" + n, "Luis Mañana", null, null, null));
    stays.save(Stay.fromReservation("MCP-T" + n, "C-MCP-T" + n, "Doble", "SA", LocalDate.now().plusDays(1),
        LocalDate.now().plusDays(2), 1, null, BigDecimal.TEN, List.of()));

    assertThat(tools.listArrivals()).noneMatch(s -> s.stayId().equals("MCP-T" + n));
  }

  @Test
  void todaysDeparturesAreTheStaysLeavingToday() {
    var leaving = inHouse(LocalDate.now());
    var staying = inHouse(LocalDate.now().plusDays(2));

    assertThat(tools.listDepartures()).extracting(FrontDeskMcpTools.StaySummary::stayId)
        .contains(leaving).doesNotContain(staying);
    assertThat(tools.listInHouse()).extracting(FrontDeskMcpTools.StaySummary::stayId).contains(leaving, staying);
  }

  @Test
  void aStayIsFoundByItsIdOrByTheCrsLocatorOfItsWalkIn() {
    var stayId = arrival(1);
    walkIns.save(WalkIn.pending(stayId, "{}", BigDecimal.TEN, Instant.now()).booked("LOC-" + stayId, Instant.now()));

    assertThat(tools.getStay(stayId).stay().stayId()).isEqualTo(stayId);
    assertThat(tools.getStay("LOC-" + stayId).stay().stayId()).isEqualTo(stayId);
    assertThat(tools.getStay(stayId).walkIn()).contains("LOC-" + stayId);
  }

  @Test
  void theGuestAndTheFolioOfAStay() {
    var stayId = inHouse(LocalDate.now().plusDays(1));

    var guest = tools.getGuest(stayId);
    assertThat(guest.name()).startsWith("Ana MCP");
    assertThat(guest.kardex()).isNull();

    var folio = tools.getFolio(stayId);
    assertThat(folio.lines()).extracting(FrontDeskMcpTools.FolioLineView::concept).contains("Alojamiento x2 noches");
    assertThat(folio.lateCheckOutContracted()).isFalse();
  }

  @Test
  void onlyFreeRoomsAreAvailable() {
    var free = room(RoomOccupancy.FREE);
    var taken = room(RoomOccupancy.OCCUPIED);

    assertThat(tools.listAvailableRooms(null)).extracting(FrontDeskMcpTools.RoomView::number)
        .contains(free).doesNotContain(taken);
    assertThat(tools.listAvailableRooms("suite mcp")).extracting(FrontDeskMcpTools.RoomView::number).contains(free);
  }

  // ── confirmar antes de escribir ───────────────────────────────────────────────

  @Test
  void aLateCheckOutIsOnlyChargedOnceThePersonConfirmsInALaterMessage() {
    var stayId = inHouse(LocalDate.now().plusDays(1));

    var prepared = tools.prepareLateCheckOut(stayId);
    assertThat(prepared).contains("PENDIENTE DE CONFIRMACIÓN").contains("50.00");
    var token = token(prepared);
    assertThat(lateCheckOut(stayId)).isFalse();

    // The model confirming for itself, in the message it prepared it in: refused, nothing done.
    assertThat(tools.confirmAction(token)).startsWith("Error: la persona todavía no ha confirmado");
    assertThat(lateCheckOut(stayId)).isFalse();

    caller.turn = "turn-" + SEQ.incrementAndGet(); // the person answered "sí"
    assertThat(tools.confirmAction(token)).startsWith("Hecho.");
    assertThat(lateCheckOut(stayId)).isTrue();

    // Single use.
    assertThat(tools.confirmAction(token)).startsWith("Error: no hay ninguna operación pendiente");
    var audited = audited(stayId);
    assertThat(audited).hasSize(1);
    assertThat(audited.get(0)).contains("\"action\":\"Late check-out\"").contains("\"by\":\"reception-agent (ana)\"")
        .contains("\"succeeded\":true").contains("\"service\":\"front-office\"");
  }

  @Test
  void inTheNextMessageThePendingOperationAndItsTokenAreThereToBeFound() {
    var stayId = inHouse(LocalDate.now().plusDays(1));
    var token = token(tools.prepareLateCheckOut(stayId));

    caller.turn = "turn-" + SEQ.incrementAndGet(); // "sí" — and the agent no longer has the token
    assertThat(tools.listPendingActions()).anySatisfy(p -> {
      assertThat(p.token()).isEqualTo(token);
      assertThat(p.action()).isEqualTo("Late check-out");
      assertThat(p.summary()).contains(stayId);
    });
    caller.person = "luis";
    assertThat(tools.listPendingActions()).noneMatch(p -> p.token().equals(token));

    caller.person = "ana";
    assertThat(tools.confirmAction(token)).startsWith("Hecho.");
    assertThat(tools.listPendingActions()).noneMatch(p -> p.token().equals(token));
  }

  @Test
  void whatOnePersonPreparedAnotherCannotConfirm() {
    var stayId = inHouse(LocalDate.now().plusDays(1));
    var token = token(tools.prepareLateCheckOut(stayId));

    caller.person = "luis";
    caller.turn = "turn-" + SEQ.incrementAndGet();

    assertThat(tools.confirmAction(token)).contains("la preparó otra persona");
    assertThat(lateCheckOut(stayId)).isFalse();
  }

  @Test
  void aCancelledOperationIsNeverDone() {
    var stayId = inHouse(LocalDate.now().plusDays(1));
    var token = token(tools.prepareLateCheckOut(stayId));

    assertThat(tools.cancelAction(token)).startsWith("Cancelado");
    caller.turn = "turn-" + SEQ.incrementAndGet();

    assertThat(tools.confirmAction(token)).startsWith("Error: no hay ninguna operación pendiente");
    assertThat(lateCheckOut(stayId)).isFalse();
    assertThat(audited(stayId)).isEmpty();
  }

  @Test
  void whatCannotBeDoneIsNotEvenPrepared() {
    var arriving = arrival(1);

    assertThat(tools.prepareLateCheckOut(arriving)).startsWith("No se puede preparar").doesNotContain("token");
    assertThat(tools.prepareCheckIn(arriving, null, null)).startsWith("No se puede preparar").contains("sin identidad");
    assertThat(tools.prepareNoShow(arriving, 3, true)).startsWith("No se puede preparar");
    assertThat(tools.prepareRoomChange(arriving, room(RoomOccupancy.OCCUPIED))).startsWith("No se puede preparar");
    // No CRS in the tests: the walk-in cannot even be quoted.
    assertThat(tools.prepareWalkIn(LocalDate.now(), LocalDate.now().plusDays(1), "DBL", "BAR", "BB", 2, null,
        "Eva", "Ruiz", "PASSPORT", "P123", null, null, "ES")).startsWith("No se puede preparar");
    assertThat(tools.prepareWalkIn(LocalDate.now(), LocalDate.now().plusDays(1), "DBL", "BAR", "BB", 2, null,
        "Eva", null, null, null, null, null, null)).contains("apellidos").contains("documento");
  }

  @Test
  void theKardexThenTheCheckInEachConfirmed() {
    var stayId = arrival(1);

    var kardex = tools.prepareKardexEdit(stayId, 1, "Y7654321", null, null, "+34 600 000 000");
    assertThat(kardex).contains("documento").contains("Y7654321").contains("Salesforce");
    confirmNextTurn(token(kardex));
    assertThat(guests.findById(stays.findById(stayId).orElseThrow().guestId()).orElseThrow().identityComplete()).isTrue();

    var room = room(RoomOccupancy.FREE);
    var prepared = tools.prepareCheckIn(stayId, room, List.of());
    assertThat(prepared).contains("Check-in de " + stayId).contains("habitación " + room);
    assertThat(stays.findById(stayId).orElseThrow().status()).isEqualTo(StayStatus.ARRIVING);

    assertThat(confirmNextTurn(token(prepared))).startsWith("Hecho.");
    var stay = stays.findById(stayId).orElseThrow();
    assertThat(stay.status()).isEqualTo(StayStatus.IN_HOUSE);
    assertThat(stay.roomNumber()).isEqualTo(room);
    assertThat(audited(stayId)).hasSize(2);
  }

  @Test
  void aNoShowOfTheWholeReservationSaysTheCrsIsTold() {
    var stayId = arrival(1);

    assertThat(tools.prepareNoShow(stayId, 1, true)).contains("NO SHOW DE TODA LA RESERVA").contains("CRS");
  }

  @Test
  void anOperationThatNoLongerHoldsIsRefusedAndAuditedAsRefused() {
    var stayId = inHouse(LocalDate.now().plusDays(1));
    var target = room(RoomOccupancy.FREE);
    var token = token(tools.prepareRoomChange(stayId, target));

    // Someone else took the room before the person confirmed.
    rooms.save(rooms.findByNumber(target).orElseThrow().occupy());

    assertThat(confirmNextTurn(token)).startsWith("Error: no se ha podido hacer");
    assertThat(stays.findById(stayId).orElseThrow().roomNumber()).isNotEqualTo(target);
    assertThat(audited(stayId)).singleElement().asString().contains("\"succeeded\":false")
        .contains("\"action\":\"Room change\"");
  }

  @Test
  void theServerTellsTheAgentHowToConfirm() {
    assertThat(tools.systemContext()).contains("confirmAction").contains("prepare");
  }

  // ── ───────────────────────────────────────────────────────────────────────────

  String confirmNextTurn(String token) {
    caller.turn = "turn-" + SEQ.incrementAndGet();
    return tools.confirmAction(token);
  }

  static String token(String prepared) {
    var m = TOKEN.matcher(prepared);
    assertThat(m.find()).as("a token in: " + prepared).isTrue();
    return m.group(1);
  }

  boolean lateCheckOut(String stayId) {
    return folios.findByStayId(stayId).orElseThrow().lateCheckOutContracted();
  }

  List<String> audited(String stayId) {
    // The parameters travel as a JSON string inside the action: {\"stayId\":\"MCP-1\",…}
    var needle = "\\\"stayId\\\":\\\"" + stayId + "\\\"";
    return jdbc.queryForList("select payload from audit_outbox", String.class).stream()
        .filter(p -> p.contains(needle)).toList();
  }

  String arrival(int pax) {
    var n = SEQ.incrementAndGet();
    var guestId = "C-MCP" + n;
    guests.save(Guest.fromReservation(guestId, "Ana MCP " + n, null, "ana" + n + "@example.com", null));
    var stayId = "MCP-" + n;
    stays.save(Stay.fromReservation(stayId, guestId, "Doble", "Desayuno", LocalDate.now(), LocalDate.now().plusDays(2),
        pax, "Directo · WEB", new BigDecimal("200.00"), List.of()));
    return stayId;
  }

  String inHouse(LocalDate checkOut) {
    var n = SEQ.incrementAndGet();
    var guestId = "C-MCP" + n;
    guests.save(Guest.fromReservation(guestId, "Ana MCP " + n, "X" + n, null, null).verifyIdentity("X" + n));
    var stayId = "MCP-" + n;
    var room = room(RoomOccupancy.FREE);
    stays.save(Stay.fromReservation(stayId, guestId, "Doble", "Desayuno", checkOut.minusDays(2), checkOut, 1,
        null, new BigDecimal("200.00"), List.of()));
    checkIn.checkIn(stayId, room, List.of());
    return stayId;
  }

  String room(RoomOccupancy occupancy) {
    var number = String.valueOf(8800 + SEQ.incrementAndGet());
    rooms.save(new Room(number, 88, "Suite MCP", occupancy, HousekeepingStatus.CLEAN, null));
    return number;
  }
}
