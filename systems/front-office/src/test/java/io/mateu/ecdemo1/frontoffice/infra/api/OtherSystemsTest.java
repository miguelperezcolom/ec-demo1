package io.mateu.ecdemo1.frontoffice.infra.api;

import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.frontoffice.infra.mdm.CrossLinks;
import io.mateu.ecdemo1.frontoffice.ui.common.OtherSystems;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.Text;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A stay in the chain's other systems: the stays the MDM asks for by a customer's code, and the
 * links a stay shows — what the MDM, played by a small server, says its people are.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:other-systems;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "frontoffice.console-url=https://ec1.example.test/"})
@AutoConfigureMockMvc
class OtherSystemsTest {

  static HttpServer mdm;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws IOException {
    mdm = HttpServer.create(new InetSocketAddress(0), 0);
    mdm.createContext("/", exchange -> {
      var path = exchange.getRequestURI().getPath();
      var body = path.equals("/reservations/MRU01/RES9/links")
          ? """
            {"hotelCode":"MRU01","locator":"RES9","passengers":[
              {"passenger":0,"role":"HOLDER","customerId":"C-ANA","name":"Ana <García>","status":"PROVISIONAL",
               "customerRoute":"/customers/search/C-ANA","salesforceContactId":"003ANA",
               "salesforceContactUrl":"https://acme.lightning.force.com/lightning/r/Contact/003ANA/view"},
              {"passenger":1,"role":"GUEST","customerId":"C-LEO","name":"Leo García","status":"PROVISIONAL",
               "customerRoute":"/customers/search/C-LEO","salesforceContactId":null,"salesforceContactUrl":null}],
             "operaProfiles":[{"profileId":"20538296","customerId":"C-ANA","context":"XMAR/RES9"}],
             "frontOffice":null,"somethingNew":true}"""
          : null;
      var bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length);
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

  static String reservation(String holder, String companion, String checkIn, String checkOut) {
    return """
        {"holder":{"customerId":"%s","name":"%s","document":null,"email":null,"phone":null},
         "companions":[{"customerId":"%s","name":"%s"}],
         "roomType":"Suite","board":"Solo alojamiento","checkIn":"%s","checkOut":"%s","pax":2,
         "agency":null,"total":720.00}""".formatted(holder, holder, companion, companion, checkIn, checkOut);
  }

  @Test
  void aCustomersStaysAreTheOnesItIsTheGuestOfAndTheOnesItRoomsIn() throws Exception {
    mvc.perform(put("/api/reservations/RES7").contentType(MediaType.APPLICATION_JSON)
        .content(reservation("C-ANA7", "C-LEO7", "2026-11-10", "2026-11-13"))).andExpect(status().isOk());
    mvc.perform(put("/api/reservations/RES8").contentType(MediaType.APPLICATION_JSON)
        .content(reservation("C-LEO7", "C-EVA7", "2026-12-01", "2026-12-04"))).andExpect(status().isOk());

    mvc.perform(get("/api/guests/C-ANA7/stays")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("RES7"))
        .andExpect(jsonPath("$[0].role").value("HOLDER"))
        .andExpect(jsonPath("$[0].status").value("ARRIVING"));
    // Latest first: the one it is the guest of, then the one it rooms in.
    mvc.perform(get("/api/guests/C-LEO7/stays")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value("RES8"))
        .andExpect(jsonPath("$[0].role").value("HOLDER"))
        .andExpect(jsonPath("$[1].id").value("RES7"))
        .andExpect(jsonPath("$[1].role").value("COMPANION"));
    mvc.perform(get("/api/guests/C-NOBODY/stays")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void aStayLinksToItsCrsBookingItsCustomersAndTheirSalesforceContacts() {
    assertThat(CrossLinks.of("RES9")).extracting(CrossLinks.Link::what, CrossLinks.Link::href).containsExactly(
        org.assertj.core.groups.Tuple.tuple("Reserva en el CRS", "https://ec1.example.test/booking/bookings/RES9"),
        org.assertj.core.groups.Tuple.tuple("Titular · cliente", "https://ec1.example.test/customers/search/C-ANA"),
        org.assertj.core.groups.Tuple.tuple("Titular · Salesforce", "https://acme.lightning.force.com/lightning/r/Contact/003ANA/view"),
        org.assertj.core.groups.Tuple.tuple("Huésped 2 · cliente", "https://ec1.example.test/customers/search/C-LEO"),
        // Opera Cloud has no stable deep link: the profile is a reference to copy.
        org.assertj.core.groups.Tuple.tuple("Opera · perfil", null));

    var block = OtherSystems.of("RES9");
    assertThat(block).isInstanceOf(Element.class);
    var html = ((Element) block).content();
    assertThat(html).contains("<a href=\"https://ec1.example.test/booking/bookings/RES9\" target=\"_blank\"")
        .contains("Ana &lt;García&gt; · C-ANA").doesNotContain("<García>").contains("20538296");
  }

  @Test
  void aStayTheMdmDoesNotKnowStillLinksToItsCrsBookingAndADemoStayToNothing() {
    assertThat(CrossLinks.of("RES-UNKNOWN")).extracting(CrossLinks.Link::what).containsExactly("Reserva en el CRS");
    assertThat(CrossLinks.of("demo-123-1")).isEmpty();
    assertThat(OtherSystems.of("demo-123-1")).isInstanceOf(Text.class);
  }
}
