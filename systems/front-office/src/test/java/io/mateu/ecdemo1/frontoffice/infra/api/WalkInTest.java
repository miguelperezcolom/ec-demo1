package io.mateu.ecdemo1.frontoffice.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn.WalkInStatus;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.crs.WalkInDesk;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A walk-in: priced by the CRS, opened here at once, booked by the CRS under the stay's reference,
 * and recognised when the booking comes back down the chain. The CRS's adapter is played by a small
 * server: it answers the offer and the quote, and books — or does not answer, or refuses — as told.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:walk-in;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "frontoffice.walk-in-resend=1h"})
@AutoConfigureMockMvc
class WalkInTest {

  static final List<String> booked = new CopyOnWriteArrayList<>();
  /** 201 books it; 500 does not answer; 409 refuses it. */
  static final AtomicInteger bookingAnswer = new AtomicInteger(201);
  static HttpServer crs;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws IOException {
    crs = HttpServer.create(new InetSocketAddress(0), 0);
    crs.createContext("/", exchange -> {
      var path = exchange.getRequestURI().getPath();
      var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      int code = 200;
      String answer;
      if (path.equals("/walk-ins/offer")) {
        answer = """
            {"hotelCode":"MRU01","hotelName":"Riu Le Morne","roomTypes":[{"code":"STD-KING","name":"Estándar con cama king"}],
             "ratePlans":[{"code":"DIRECTA","name":"Directa"}],"boards":[{"code":"DESAYUNO","name":"Desayuno"}]}""";
      } else if (path.equals("/walk-ins/quote")) {
        answer = "{\"currency\":\"EUR\",\"nights\":2,\"total\":412.00}";
      } else {
        code = bookingAnswer.get();
        booked.add(body);
        answer = switch (code) {
          case 201 -> "{\"locator\":\"CRS123\",\"reference\":\"x\"}";
          case 409 -> "{\"status\":409,\"detail\":\"The price changed: quoted 412.00 EUR, the CRS prices it at 430.00 EUR now\"}";
          default -> "{}";
        };
      }
      var bytes = answer.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", code == 409 ? "application/problem+json" : "application/json");
      exchange.sendResponseHeaders(code, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    crs.start();
    registry.add("frontoffice.crs-integration-url", () -> "http://localhost:" + crs.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    crs.stop(0);
  }

  @Autowired MockMvc mvc;
  @Autowired WalkInDesk desk;
  @Autowired WalkIns walkIns;
  @Autowired StayRepository stays;
  @Autowired GuestRepository guests;

  @BeforeEach
  void reset() {
    booked.clear();
    bookingAnswer.set(201);
  }

  static WalkInDesk.Request request() {
    var today = LocalDate.now();
    return new WalkInDesk.Request(null, null, today, today.plusDays(2), "STD-KING", "DIRECTA", "DESAYUNO", 2, List.of(),
        new WalkInDesk.Holder("Nora", "Vega", "nora@example.com", "+34 600 111 222", "ES", "PASSPORT", "X1234567"),
        null);
  }

  @Test
  void theStayStepOffersWhatTheCrsSellsAsSelectsWithItsCodesAndNames() {
    var step = new io.mateu.ecdemo1.frontoffice.ui.walkin.EstanciaWalkIn();

    assertThat(step.options("habitacion", null)).extracting(io.mateu.uidl.data.Option::value).containsExactly("STD-KING");
    assertThat(step.options("habitacion", null)).extracting(io.mateu.uidl.data.Option::label)
        .containsExactly("Estándar con cama king (STD-KING)");
    assertThat(step.options("tarifa", null)).extracting(io.mateu.uidl.data.Option::value).containsExactly("DIRECTA");
    assertThat(step.options("regimen", null)).extracting(io.mateu.uidl.data.Option::value).containsExactly("DESAYUNO");
    assertThat(step.stereotype("habitacion", null)).isEqualTo(io.mateu.uidl.data.FieldStereotype.select);
  }

  @Test
  void theDeskOpensTheStayAtOnceAndTheBookingThatComesBackIsThatStay() throws Exception {
    assertThat(desk.offer().roomTypes()).extracting(WalkInDesk.Option::code).containsExactly("STD-KING");
    var quote = desk.quote(request());
    assertThat(quote.total()).isEqualByComparingTo("412.00");

    var walkIn = desk.send(desk.open(request(), quote));

    var reference = walkIn.stayId();
    assertThat(reference).matches("FO-[A-Z2-9]{6}");
    assertThat(walkIn.status()).isEqualTo(WalkInStatus.BOOKED);
    assertThat(walkIn.locator()).isEqualTo("CRS123");
    // the other systems link it by the CRS locator: its page answers to that too
    assertThat(WalkInDesk.stayIdFor("CRS123")).isEqualTo(reference);
    assertThat(WalkInDesk.stayIdFor(reference)).isEqualTo(reference);
    assertThat(WalkInDesk.stayIdFor("NOPE42")).isEqualTo("NOPE42");
    assertThat(booked).singleElement().asString()
        .contains("\"reference\":\"" + reference + "\"").contains("\"expectedTotal\":412").contains("X1234567");
    var stay = stays.findById(reference).orElseThrow();
    assertThat(stay.status()).isEqualTo(StayStatus.ARRIVING);
    assertThat(stay.roomType()).isEqualTo("Estándar con cama king");
    assertThat(stay.total()).isEqualByComparingTo("412.00");

    // The guest checks in before the booking has come back down the chain.
    stays.save(stay.assignRoom("1201", stay.roomType()).completeCheckIn());

    mvc.perform(put("/api/reservations/CRS123").contentType(MediaType.APPLICATION_JSON).content("""
            {"holder":{"customerId":"C-NORA","name":"Nora Vega","document":null,"email":"nora@example.com","phone":null},
             "companions":[],"roomType":"Estándar King","board":"Alojamiento y desayuno","checkIn":"%s","checkOut":"%s",
             "pax":2,"agency":"Directo · WALKIN","total":412.00,"externalReference":"%s","pmsReservationId":"39480001"}"""
            .formatted(LocalDate.now(), LocalDate.now().plusDays(2), reference)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stayId").value(reference))
        .andExpect(jsonPath("$.created").value(false));

    var after = stays.findById(reference).orElseThrow();
    assertThat(after.status()).isEqualTo(StayStatus.IN_HOUSE);
    assertThat(after.roomNumber()).isEqualTo("1201");
    assertThat(after.guestId()).isEqualTo("C-NORA");
    assertThat(stays.findById("CRS123")).isEmpty();
    // What the desk took down goes on with the chain's customer.
    assertThat(guests.findById("C-NORA").orElseThrow().document()).isEqualTo("X1234567");
    var cameBack = walkIns.of(reference).orElseThrow();
    assertThat(cameBack.pmsReservationId()).isEqualTo("39480001");
    assertThat(cameBack.label()).isEqualTo("Walk-in · CRS CRS123 · Opera 39480001");
    // Asked by the CRS's locator, the front office answers with the stay it is.
    mvc.perform(get("/api/reservations/CRS123")).andExpect(jsonPath("$.stayId").value(reference));
  }

  @Test
  void aCrsThatDoesNotAnswerIsAskedAgainAndARefusalIsTheDesks() {
    var quote = new WalkInDesk.Quote("EUR", 2, new BigDecimal("412.00"));
    bookingAnswer.set(500);

    var waiting = desk.send(desk.open(request(), quote));
    assertThat(waiting.status()).isEqualTo(WalkInStatus.PENDING);
    assertThat(stays.findById(waiting.stayId())).isPresent();

    bookingAnswer.set(201);
    desk.resend();
    assertThat(walkIns.of(waiting.stayId()).orElseThrow().status()).isEqualTo(WalkInStatus.BOOKED);
    // The same booking both times: the reference is the stay's.
    assertThat(booked).hasSize(2).allSatisfy(b -> assertThat(b).contains(waiting.stayId()));

    bookingAnswer.set(409);
    var refused = desk.send(desk.open(request(), quote));
    assertThat(refused.status()).isEqualTo(WalkInStatus.REFUSED);
    assertThat(refused.label()).contains("The price changed");
  }
}
