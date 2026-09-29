package io.mateu.ecdemo1.mdm.ui.data;

import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.integration.model.customer.IdentityRequest;
import io.mateu.ecdemo1.integration.model.reservation.GuestType;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.mdm.change.Xrefs;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.Source;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import io.mateu.ecdemo1.mdm.store.Xref;
import io.mateu.ecdemo1.mdm.store.XrefRepository;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.SearchRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clientes, the data plane's screens of the customer master: finding a customer, and its page with
 * where it is and its reservations in the CRS and the front office — which one small double answers
 * for — and the links other systems ask the MDM for.
 */
@SpringBootTest(properties = {"mdm.projection-tick=1h", "mdm.poll=1h", "mdm.change-tick=1h", "mdm.change-poll=1h",
        "outbox.interval=1h", "mdm.links.salesforce-url=https://acme.lightning.force.com",
        "mdm.links.front-office-public-url=https://front.example.test/"})
@AutoConfigureMockMvc
@Testcontainers
class CustomersScreensTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static HttpServer others;
    /** What the CRS and the front office answer, by path. */
    static final Map<String, String> answers = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        others = HttpServer.create(new InetSocketAddress(0), 0);
        others.createContext("/", exchange -> {
            var body = answers.get(exchange.getRequestURI().getPath());
            var bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        others.start();
        var base = "http://localhost:" + others.getAddress().getPort();
        registry.add("mdm.links.booking-url", () -> base);
        registry.add("mdm.links.front-office-url", () -> base);
    }

    @AfterAll
    static void stop() {
        others.stop(0);
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    IdentityResolution resolution;
    @Autowired
    CustomerRepository customers;
    @Autowired
    SourceRepository sources;
    @Autowired
    XrefRepository xrefRepository;
    @Autowired
    Xrefs xrefs;
    @Autowired
    ChangeRequestRepository changeRequests;

    @BeforeEach
    void clean() {
        answers.clear();
        changeRequests.deleteAll();
        xrefRepository.deleteAll();
        sources.deleteAll();
        customers.deleteAll();
    }

    static Person person(String first, String last, String email, String phone, String docType, String doc) {
        return new Person(first, last, GuestType.ADULT, null, email, phone, "ES", null, docType, doc);
    }

    String resolve(String locator, Person... passengers) {
        return resolution.resolve(new IdentityRequest("MRU01", locator, List.of(passengers))).get(0).customerId();
    }

    List<String> found(String text, CustomerFilters filters) {
        var page = context.getBean(CustomerSearchPage.class)
                .search(new SearchRequest(text, filters, null, new Pageable(0, 20, List.of())), null);
        return page.page().content().stream().map(CustomerSearchRow::id).toList();
    }

    static CustomerFilters filters(String name, String email, String phone, String document) {
        var f = new CustomerFilters();
        f.name = name;
        f.email = email;
        f.phone = phone;
        f.document = document;
        return f;
    }

    @Test
    void aCustomerIsFoundByNameEmailPhoneOrDocument() {
        var ana = resolve("B1", person("Ana", "Vidal Roca", "ana.vidal@example.com", "+34 600 11 22 33", "DNI", "12345678-Z"));
        var luis = resolve("B2", person("Luis", "Pons", "luis@example.com", "971 000 000", "PAS", "X1"));

        assertThat(found("", null)).containsExactlyInAnyOrder(ana, luis);
        assertThat(found("", filters("roca ana", null, null, null))).containsExactly(ana);
        assertThat(found("", filters(null, "ANA.VIDAL@", null, null))).containsExactly(ana);
        // A phone is its digits, however it was written.
        assertThat(found("", filters(null, null, "600112233", null))).containsExactly(ana);
        assertThat(found("", filters(null, null, "(971) 000-000", null))).containsExactly(luis);
        // A document too, without dashes, spaces or case.
        assertThat(found("", filters(null, null, null, "12345678z"))).containsExactly(ana);
        // The free text looks everywhere; every word has to be found.
        assertThat(found("vidal 600", null)).containsExactly(ana);
        assertThat(found("luis@", null)).containsExactly(luis);
        assertThat(found("nadie", null)).isEmpty();

        var consolidated = new CustomerFilters();
        consolidated.status = Set.of(CustomerFilters.Estado.CONSOLIDATED);
        assertThat(found("", consolidated)).isEmpty();
        consolidated.status = Set.of(CustomerFilters.Estado.PROVISIONAL);
        assertThat(found("", consolidated)).containsExactlyInAnyOrder(ana, luis);
    }

    @Test
    void aMergedCustomerIsNotListedAndItsCodeOpensTheCustomerItBecamePartOf() {
        var ana = resolve("B1", person("Ana", "Vidal", "ana@example.com", null, null, null));
        var absorbed = absorbedInto(ana, "B9");

        assertThat(found("", null)).containsExactly(ana);
        var card = context.getBean(CustomerSearchPage.class).view(absorbed, null);
        assertThat(card.id()).isEqualTo(ana);
        assertThat(card.aliases).isEqualTo(absorbed);
        // The absorbed code's reservation is the customer's too.
        assertThat(card.systemsMarkup()).contains("href=\"/booking/bookings/B9\"");
    }

    @Test
    void theCustomersPageShowsWhereItIsAndItsReservationsInEverySystemWithTheirLinks() {
        var ana = resolve("B1", person("Ana", "Vidal", "ana@example.com", null, "DNI", "1Z"),
                person("Leo", "Vidal", null, null, null, null));
        var leo = sources.findByHotelCodeAndLocatorOrderByPassengerAsc("MRU01", "B1").get(1).customerId;
        resolve("B2", person("Eva", "Soler", "eva@example.com", null, null, null), person("Ana", "Vidal", "ana@example.com", null, null, null));
        withContact(ana, "003ANA");
        xrefs.record(ana, Xref.Target.OPERA, "20538296", "XMAR/B1");
        xrefs.record(ana, Xref.Target.FRONT_OFFICE, ana, "MRU01");
        answers.put("/bookings/B1", booking("B1", "Confirmed", "2026-11-10", "2026-11-13", "123456"));
        answers.put("/bookings/B2", booking("B2", "Cancelled", "2026-12-01", "2026-12-03", null));
        answers.put("/api/guests/" + ana + "/stays", """
                [{"id":"B1","role":"HOLDER","checkIn":"2026-11-10","checkOut":"2026-11-13","room":"204","roomType":"Suite","status":"IN_HOUSE"}]""");
        var request = changeRequest(ana, "email: ana@example.com → ana.vidal@example.com", "500CASE");

        var card = context.getBean(CustomerSearchPage.class).view(ana, null);

        assertThat(card.status.message()).isEqualTo("Provisional");
        assertThat(card.document).isEqualTo("DNI 1Z");
        var markup = card.systemsMarkup();
        assertThat(markup)
                // where it is: its Salesforce contact, its guest in the front office, its Opera profile
                .contains("href=\"https://acme.lightning.force.com/lightning/r/Contact/003ANA/view\" target=\"_blank\"")
                .contains("Huésped " + ana)
                .contains("Perfil 20538296")
                // its reservations: the CRS booking on this console, the stay in the front office, Opera's ids
                .contains("href=\"/booking/bookings/B1\"")
                .contains("href=\"https://front.example.test/reservas/B1\"")
                .contains("En casa · hab. 204")
                .contains("Reserva 123456")
                .contains("Titular")
                .contains("href=\"/booking/bookings/B2\"")
                .contains("Huésped</td>")
                .contains("Cancelada")
                // the change it was asked for, and its Case
                .contains("href=\"https://acme.lightning.force.com/lightning/r/Case/500CASE/view\"");
        assertThat(card.changeRequests).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(request);
            assertThat(row.status().message()).isEqualTo("Pendiente");
            assertThat(row.salesforceCase()).isEqualTo("500CASE");
        });
        assertThat(leo).isNotEqualTo(ana);
    }

    @Test
    void whatComesFromDataIsEscaped() {
        var evil = resolve("B1", person("<script>x</script>", "${state.x}", null, null, null, null));
        xrefs.record(evil, Xref.Target.OPERA, "<b>1</b>", "XMAR/B1");
        xrefs.record(evil, Xref.Target.OPERA, "${state.x}", "XMAR/B1");

        var markup = context.getBean(CustomerSearchPage.class).view(evil, null).systemsMarkup();

        assertThat(markup).doesNotContain("<b>1</b>").contains("&lt;b&gt;1&lt;/b&gt;");
        assertThat(markup).doesNotContain("${");
    }

    @Test
    void aReservationsPeopleAreLinkedForTheScreensOfOtherSystems() throws Exception {
        var ana = resolve("B1", person("Ana", "Vidal", "ana@example.com", null, null, null),
                person("Leo", "Vidal", null, null, null, null));
        withContact(ana, "003ANA");
        xrefs.record(ana, Xref.Target.OPERA, "20538296", "XMAR/B1");
        xrefs.record(ana, Xref.Target.OPERA, "999", "XMAR/OTHER");
        answers.put("/api/reservations/B1", """
                {"locator":"B1","guestId":"%s","status":"ARRIVING","created":false}""".formatted(ana));

        mvc.perform(get("/reservations/MRU01/B1/links")).andExpect(status().isOk())
                .andExpect(jsonPath("$.passengers.length()").value(2))
                .andExpect(jsonPath("$.passengers[0].role").value("HOLDER"))
                .andExpect(jsonPath("$.passengers[0].customerId").value(ana))
                .andExpect(jsonPath("$.passengers[0].customerRoute").value("/customers/search/" + ana))
                .andExpect(jsonPath("$.passengers[0].salesforceContactUrl")
                        .value("https://acme.lightning.force.com/lightning/r/Contact/003ANA/view"))
                .andExpect(jsonPath("$.passengers[1].role").value("GUEST"))
                .andExpect(jsonPath("$.passengers[1].salesforceContactUrl").doesNotExist())
                .andExpect(jsonPath("$.operaProfiles.length()").value(1))
                .andExpect(jsonPath("$.operaProfiles[0].profileId").value("20538296"))
                .andExpect(jsonPath("$.frontOffice.status").value("ARRIVING"))
                .andExpect(jsonPath("$.frontOffice.url").value("https://front.example.test/reservas/B1"));

        // A reservation the MDM never saw, and no stay: nothing to link to, and no error.
        mvc.perform(get("/reservations/MRU01/NOPE/links")).andExpect(status().isOk())
                .andExpect(jsonPath("$.passengers.length()").value(0))
                .andExpect(jsonPath("$.frontOffice").doesNotExist());
    }

    @Test
    void theChangeRequestsListingOpensTheirCustomer() {
        var ana = resolve("B1", person("Ana", "Vidal", "ana@example.com", null, null, null));
        var request = changeRequest(ana, "teléfono: — → 600", null);

        var page = context.getBean(ChangeRequestsPage.class);
        var rows = page.search(new SearchRequest("vidal", null, null, new Pageable(0, 20, List.of())), null)
                .page().content();
        assertThat(rows).singleElement().satisfies(row -> assertThat(row.customer()).contains(ana));
        assertThat(page.view(request, null).id()).isEqualTo(ana);

        var approved = new ChangeRequestsPage.Filters();
        approved.status = Set.of(ChangeRequest.Status.APPROVED);
        assertThat(page.search(new SearchRequest("", approved, null, new Pageable(0, 20, List.of())), null)
                .page().content()).isEmpty();
    }

    /** What a shell gets for a route of Clientes, as its renderer asks for it. */
    String wire(String route, String consumedRoute, String serverSideType) throws Exception {
        var body = json.writeValueAsString(Map.of("route", route, "consumedRoute", consumedRoute, "actionId", "",
                "componentState", Map.of(), "appState", Map.of(), "serverSideType", serverSideType));
        var started = mvc.perform(post("/_customers/mateu/v3/sync" + route).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        return mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theScreensRenderOverTheWire() throws Exception {
        var ana = resolve("B1", person("Ana", "Vidal", "ana@example.com", null, null, null));
        changeRequest(ana, "email: a → b", "500CASE");

        for (var route : List.of("/customers/search", "/customers/changes")) {
            assertThat(wire(route, "", CustomersHome.class.getName())).contains("\"route\":\"" + route + "\"")
                    .doesNotContain("Not found.").doesNotContain("\"variant\":\"error\"");
        }
        // The customer's page: its data as fields, its links and its reception notices as Elements, its
        // change requests as a grid; and the form to ask for a notice, with its actions.
        var page = wire("/customers/search/" + ana, "/customers/search", CustomerSearchPage.class.getName());
        assertThat(page).doesNotContain("\"variant\":\"error\"")
                .contains("Datos vigentes").contains("Solicitudes de cambio")
                .contains("href=\\\"/booking/bookings/B1\\\"")
                .contains("CR-" + ana)
                .contains("Avisos de recepción").contains("No tiene avisos").contains("guardarAviso");
        assertThat(page.split("\"type\":\"Element\"", -1)).hasSize(3);
    }

    void withContact(String customerId, String contactId) {
        var c = customers.findById(customerId).orElseThrow();
        c.salesforceContactId = contactId;
        c.salesforceState = SalesforceState.PROJECTED;
        customers.save(c);
    }

    /** A customer merged into another, with a reservation of its own. */
    String absorbedInto(String survivor, String locator) {
        var c = new Customer();
        c.id = "C-ABSORBED" + locator;
        c.status = CustomerStatus.MERGED;
        c.aliasOf = survivor;
        c.firstName = "Ana";
        c.lastName = "V.";
        c.updatedAt = Instant.now();
        c.salesforceState = SalesforceState.NOT_PROJECTED;
        customers.save(c);
        var s = new Source();
        s.sourceKey = Source.key("MRU01", locator, 0);
        s.customerId = c.id;
        s.firstCustomerId = c.id;
        s.hotelCode = "MRU01";
        s.locator = locator;
        s.passenger = 0;
        s.firstSeen = Instant.now();
        s.lastSeen = s.firstSeen;
        sources.save(s);
        return c.id;
    }

    String changeRequest(String customerId, String changes, String caseId) {
        var r = new ChangeRequest();
        r.id = "CR-" + customerId;
        r.customerId = customerId;
        r.origin = "front office MRU01 · ana";
        r.changes = changes;
        r.status = ChangeRequest.Status.PENDING.name();
        r.salesforceCaseId = caseId;
        r.requestedAt = Instant.now();
        changeRequests.save(r);
        return r.id;
    }

    static String booking(String id, String status, String arrival, String departure, String pms) {
        return """
                {"id":"%s","hotelCode":"MRU01","status":"%s","arrival":"%s","departure":"%s",
                 "holder":{"firstName":"Ana","lastName":"Vidal"},"pmsReference":%s,"somethingNew":1}"""
                .formatted(id, status, arrival, departure, pms == null ? "null" : "{\"reservationId\":\"" + pms + "\"}");
    }
}
