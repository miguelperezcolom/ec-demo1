package io.mateu.ecdemo1.frontoffice.infra.api;

import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.infra.mdm.Kardex;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The desk changes a guest; the chain's master decides. The MDM is played by a small server that
 * keeps what it is sent and answers with a change request id.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:kardex;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class KardexTest {

  static final List<String> sent = new CopyOnWriteArrayList<>();
  static HttpServer mdm;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws IOException {
    mdm = HttpServer.create(new InetSocketAddress(0), 0);
    mdm.createContext("/", exchange -> {
      sent.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
          + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      var body = "{\"id\":\"CR-" + sent.size() + "\",\"status\":\"PENDING\"}";
      var bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    mdm.start();
    registry.add("frontoffice.mdm-url", () -> "http://localhost:" + mdm.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    mdm.stop(0);
  }

  @Autowired MockMvc mvc;
  @Autowired GuestRepository guests;

  static String kardex(String name, String email, String requestId, String decision) {
    return """
        {"name":"%s","email":"%s","phone":null,"document":"12345678Z","requestId":%s,"decision":%s}"""
        .formatted(name, email, requestId == null ? "null" : "\"" + requestId + "\"", decision == null ? "null" : "\"" + decision + "\"");
  }

  Guest edit(String id, String name, String email) {
    var before = guests.findById(id).orElseThrow();
    var after = before.rename(name).updateContact(email, before.phone());
    guests.save(after);
    Kardex.edited(before, after);
    return after;
  }

  @Test
  void aChangeAtTheDeskShowsPendingAndGoesToTheMasterWhichApprovesIt() throws Exception {
    guests.save(Guest.fromReservation("C-KX1", "Ana García", "12345678Z", "ana@example.com", null));
    sent.clear();

    edit("C-KX1", "Ana María García", "ana.maria@example.com");

    // Shown at once, pending; proposed to the MDM with the new data.
    assertThat(guests.findById("C-KX1").orElseThrow().email()).isEqualTo("ana.maria@example.com");
    var change = Kardex.of("C-KX1").orElseThrow();
    assertThat(change.status()).isEqualTo(KardexChange.KardexStatus.PENDING);
    assertThat(change.changes()).contains("email ana@example.com → ana.maria@example.com");
    assertThat(sent).singleElement().asString().startsWith("POST /customers/C-KX1/change-requests")
        .contains("\"email\":\"ana.maria@example.com\"").contains("\"name\":\"Ana María García\"");
    var requestId = change.requestId();
    assertThat(requestId).isNotNull();

    // A change made in the master meanwhile does not hide the desk's pending one.
    mvc.perform(put("/api/guests/C-KX1/kardex").contentType(MediaType.APPLICATION_JSON)
        .content(kardex("Ana García", "ana@example.com", null, null))).andExpect(status().isNoContent());
    assertThat(guests.findById("C-KX1").orElseThrow().email()).isEqualTo("ana.maria@example.com");

    // Approved: the master's data — the desk's, as approved — and the cardex says so.
    mvc.perform(put("/api/guests/C-KX1/kardex").contentType(MediaType.APPLICATION_JSON)
        .content(kardex("Ana María García", "ana.maria@example.com", requestId, "APPROVED"))).andExpect(status().isNoContent());
    assertThat(Kardex.of("C-KX1").orElseThrow().status()).isEqualTo(KardexChange.KardexStatus.APPROVED);
    assertThat(guests.findById("C-KX1").orElseThrow().name()).isEqualTo("Ana María García");
  }

  @Test
  void aRejectedChangeBringsTheMastersDataBack() throws Exception {
    guests.save(Guest.fromReservation("C-KX2", "Leo Pons", "X1234567L", "leo@example.com", null));

    edit("C-KX2", "Leo Pons", "leo.nuevo@example.com");
    var requestId = Kardex.of("C-KX2").orElseThrow().requestId();

    mvc.perform(put("/api/guests/C-KX2/kardex").contentType(MediaType.APPLICATION_JSON)
        .content(kardex("Leo Pons", "leo@example.com", requestId, "REJECTED"))).andExpect(status().isNoContent());

    assertThat(guests.findById("C-KX2").orElseThrow().email()).isEqualTo("leo@example.com");
    assertThat(Kardex.of("C-KX2").orElseThrow().status()).isEqualTo(KardexChange.KardexStatus.REJECTED);
  }

  @Test
  void aGuestTheChainDoesNotKnowIsNotProposedAndAnUnknownGuestIsNotFound() throws Exception {
    guests.save(Guest.fromReservation("crs-LOCAL1", "Sin Cliente", null, "x@example.com", null));
    sent.clear();
    edit("crs-LOCAL1", "Sin Cliente", "y@example.com");
    assertThat(sent).isEmpty();
    assertThat(Kardex.of("crs-LOCAL1")).isEmpty();

    mvc.perform(put("/api/guests/C-NOBODY/kardex").contentType(MediaType.APPLICATION_JSON)
        .content(kardex("X", "x@example.com", null, null))).andExpect(status().isNotFound());
  }
}
