package io.mateu.ecdemo1.frontoffice.infra.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The demo scanner against a booking and an MDM played by one small server. */
class DemoScannerTest {

  HttpServer server;

  DemoScanner scanner(Map<String, String> answers) throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      var path = exchange.getRequestURI().getPath();
      var body = answers.get(path);
      if (body == null) {
        exchange.sendResponseHeaders(404, -1);
      } else {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
      }
      exchange.close();
    });
    server.start();
    var url = "http://localhost:" + server.getAddress().getPort();
    return new DemoScanner(url, url, "MRU01");
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  static final String BOOKING = """
      {"locator":"L1","holder":{"firstName":"Olivia","lastName":"Brown","nationality":"GB"},
       "rooms":[{"guests":[{"firstName":"Olivia","lastName":"Brown","type":"ADULT"},
                           {"firstName":"Harry","lastName":"Brown","type":"CHILD","age":6,"nationality":"GB"}]}]}""";

  @Test
  void theBookingGivesTheNationalityAndTheChildsAge() throws Exception {
    var scanner = scanner(Map.of("/reservations/MRU01/L1", BOOKING, "/customers", "[]"));
    var arrival = LocalDate.of(2026, 10, 1);

    var holder = scanner.scan(new DemoScanner.Pax("L1", 1, "Olivia Brown", null, "C-1", arrival));
    var child = scanner.scan(new DemoScanner.Pax("L1", 2, "Harry Brown", null, null, arrival));

    assertThat(holder.documentType()).isEqualTo("PASSPORT");
    assertThat(holder.nationality()).isEqualTo("GB");
    assertThat(child.birthDate()).isAfter(arrival.minusYears(7)).isBefore(arrival.minusYears(6));
    assertThat(scanner.scan(new DemoScanner.Pax("L1", 2, "Harry Brown", null, null, arrival))).isEqualTo(child);
  }

  @Test
  void aCustomerOfTheChainWithThePaxsNameAndADocumentHandsOverThatDocument() throws Exception {
    var scanner = scanner(Map.of("/reservations/MRU01/L1", BOOKING, "/customers", """
        [{"id":"C-OTHER","firstName":"Olivia","lastName":"Browne","documentNumber":"ZZ0000001"},
         {"id":"C-OLD","firstName":"Olivia","lastName":"Brown","documentType":"PASSPORT","documentNumber":"GB1234567",
          "birthDate":"1980-04-02","nationality":"GB"}]"""));

    var scanned = scanner.scan(new DemoScanner.Pax("L1", 1, "Olivia Brown", null, "C-NEW", LocalDate.of(2026, 10, 1)));

    assertThat(scanned.documentNumber()).isEqualTo("GB1234567");
    assertThat(scanned.birthDate()).isEqualTo(LocalDate.of(1980, 4, 2));
  }

  @Test
  void withNobodyToAskItStillReadsTheSameDocument() {
    var scanner = new DemoScanner("", "", "MRU01");
    var once = scanner.scan(new DemoScanner.Pax("L9", 1, "Carmen Ruiz", null, null, null));
    assertThat(scanner.scan(new DemoScanner.Pax("L9", 1, "Carmen Ruiz", "MAN-L9", null, null))).isEqualTo(once);
    // A document the stay already has is the one on the paper.
    assertThat(scanner.scan(new DemoScanner.Pax("L9", 1, "Carmen Ruiz", "12345678Z", null, null)).documentNumber())
        .isEqualTo("12345678Z");
  }
}
