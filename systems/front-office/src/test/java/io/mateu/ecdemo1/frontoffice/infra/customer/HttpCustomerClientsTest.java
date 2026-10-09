package io.mateu.ecdemo1.frontoffice.infra.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory.LookupQuery;
import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory.Outcome;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The MDM, the customer history and Riu Class as the desk's clients read them, played by one small server. */
class HttpCustomerClientsTest {

  record Answer(int status, String body) {}

  HttpServer server;
  final List<String> asked = new CopyOnWriteArrayList<>();

  String serve(Map<String, Answer> answers) throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      var uri = exchange.getRequestURI();
      var key = exchange.getRequestMethod() + " " + uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
      asked.add(key + " " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      var answer = answers.getOrDefault(key, new Answer(404, null));
      if (answer.body() == null) {
        exchange.sendResponseHeaders(answer.status(), -1);
      } else {
        var bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(answer.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
      }
      exchange.close();
    });
    server.start();
    return "http://localhost:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void theMdmSaysWhoADocumentIsNobodyOrMoreThanOne() throws Exception {
    var url = serve(Map.of(
        "GET /identities/lookup?documentNumber=X1&country=ES", new Answer(200, """
            {"customerId":"C-1","status":"ACTIVE","matchedBy":"DOCUMENT","firstName":"Ana","lastName":"García","birthDate":"1990-05-17"}"""),
        "GET /identities/lookup?email=ana@example.com", new Answer(409, """
            {"ambiguous":true,"matchedBy":"EMAIL","count":2}"""),
        "GET /identities/candidates?birthDate=1990-05-17&firstName=Ana&lastName=García", new Answer(200, """
            [{"customerId":"C-1","status":"ACTIVE","firstName":"Ana","lastName":"García","birthDate":"1990-05-17",
              "nationality":"ES","matched":["NAME","BIRTH_DATE"]}]""")));
    var mdm = new HttpCustomerDirectory(url);

    var found = mdm.lookup(LookupQuery.byDocument("X1", "ES"));
    assertThat(found.outcome()).isEqualTo(Outcome.FOUND);
    assertThat(found.customer().customerId()).isEqualTo("C-1");
    assertThat(found.customer().birthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
    assertThat(mdm.lookup(LookupQuery.byRiuClass("RC1")).outcome()).isEqualTo(Outcome.NONE);
    var ambiguous = mdm.lookup(LookupQuery.byEmail("ana@example.com"));
    assertThat(ambiguous.outcome()).isEqualTo(Outcome.AMBIGUOUS);
    assertThat(ambiguous.count()).isEqualTo(2);
    assertThat(mdm.candidates("Ana", "García", LocalDate.of(1990, 5, 17), null))
        .singleElement().satisfies(c -> assertThat(c.matched()).containsExactly("NAME", "BIRTH_DATE"));
    assertThat(mdm.candidates("Ana", "García", null, null)).isEmpty();

    assertThat(mdm.setRiuClass("C-1", "RC12345678")).isFalse(); // 404 here: not taken
    assertThat(asked).anyMatch(a -> a.startsWith("PUT /customers/C-1/xrefs")
        && a.contains("\"target\":\"RIU_CLASS\"") && a.contains("\"reference\":\"RC12345678\""));
  }

  @Test
  void aServiceThatDoesNotAnswerIsUnavailableNeverAnError() {
    var dead = "http://localhost:1";
    assertThat(new HttpCustomerDirectory(dead).lookup(LookupQuery.byEmail("a@b.c")).outcome()).isEqualTo(Outcome.UNAVAILABLE);
    assertThat(new HttpCustomerDirectory("").lookup(LookupQuery.byEmail("a@b.c")).outcome()).isEqualTo(Outcome.UNAVAILABLE);
    assertThat(new HttpCustomerDirectory(dead).candidates("A", "B", LocalDate.of(1990, 1, 1), null)).isEmpty();
    assertThat(new HttpStayHistory(dead).summary("C-1")).isEmpty();
    assertThat(new HttpLoyaltyStatus(dead).of("C-1", null)).isEmpty();
  }

  @Test
  void theHistoryAndRiuClassSummariseTheCustomer() throws Exception {
    var url = serve(Map.of(
        "GET /customers/C-1/summary", new Answer(200, """
            {"customerId":"C-1","stays":5,"nights":23,"firstStay":"2019-07-01","lastStay":"2025-08-10",
             "lastStays":[{"hotelCode":"MRU01","arrival":"2025-08-03","departure":"2025-08-10","roomNumber":"1204","roomType":"Doble"}],
             "hotels":3,"topHotel":"MRU01","spend":{"amount":640.00,"currency":"EUR"}}"""),
        "GET /members?customerCode=C-1", new Answer(200, """
            {"memberNumber":"RC1","customerCode":"C-1","tier":"GOLD","points":12500,"memberSince":"2019-01-01","asOf":"2026-10-09"}"""),
        "GET /members/RC9", new Answer(404, null)));

    var summary = new HttpStayHistory(url).summary("C-1").orElseThrow();
    assertThat(summary.stays()).isEqualTo(5);
    assertThat(summary.lastStays()).singleElement().satisfies(l -> assertThat(l.roomNumber()).isEqualTo("1204"));
    assertThat(summary.topHotel()).isEqualTo("MRU01");

    var riuClass = new HttpLoyaltyStatus(url);
    assertThat(riuClass.of("C-1", null)).get().satisfies(l -> {
      assertThat(l.tier()).isEqualTo("GOLD");
      assertThat(l.points()).isEqualTo(12500);
      assertThat(l.source()).isEqualTo("Riu Class");
    });
    assertThat(riuClass.of("C-1", "RC9")).isEmpty();
  }
}
