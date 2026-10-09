package io.mateu.ecdemo1.mdm.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.command.CustomerCommand.RecordScannedIdentity;
import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.integration.model.customer.IdentityRequest;
import io.mateu.ecdemo1.integration.model.customer.ResolvedIdentity;
import io.mateu.ecdemo1.integration.model.reservation.GuestType;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.mdm.commands.CustomerCommands;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A customer's several documents, and the desk's lookups — exact (document, email, Riu Class) and by
 * name and birth date — against a real Postgres. Salesforce is a double that knows nobody: nothing here
 * waits for it.
 */
@SpringBootTest(properties = {"mdm.projection-tick=1h", "mdm.poll=1h", "mdm.propagation-tick=1h", "mdm.change-poll=1h",
        "mdm.refresh-tick=1h", "mdm.change-tick=1h", "mdm.notice-tick=1h", "mdm.notice-poll=1h", "mdm.salesforce-merge-tick=1h",
        "mdm.marking-tick=1h", "mdm.cleanup.enabled=false",
        "mdm.salesforce.client-id=test", "mdm.salesforce.client-secret=secret", "mdm.salesforce.subscribe=false"})
@AutoConfigureMockMvc
@Testcontainers
class IdentityLookupTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static HttpServer salesforce;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        salesforce = HttpServer.create(new InetSocketAddress(0), 0);
        salesforce.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            var base = "http://localhost:" + salesforce.getAddress().getPort();
            var code = 200;
            String body;
            if (path.equals("/services/oauth2/token")) {
                body = """
                        {"access_token":"t0k3n","instance_url":"%s","id":"%s/id/00DORG/005USER"}""".formatted(base, base);
            } else if (path.endsWith("/queryAll") || path.endsWith("/query")) {
                body = "{\"done\":true,\"records\":[]}";
            } else {
                code = 404;
                body = "{}";
            }
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        salesforce.start();
        registry.add("mdm.salesforce.domain", () -> "http://localhost:" + salesforce.getAddress().getPort());
        registry.add("outbox.interval", () -> "1h");
    }

    @AfterAll
    static void stop() {
        salesforce.stop(0);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate db;
    @Autowired
    CustomerRepository customers;
    @Autowired
    CustomerDocuments documents;
    @Autowired
    DocumentsMigration migration;
    @Autowired
    CustomerCommands commands;
    @Autowired
    ConsolidationRepository consolidations;
    @Autowired
    ChangeRequestRepository changeRequests;

    @BeforeEach
    void clean() {
        for (var table : List.of("outbox_message", "consolidation", "change_request", "customer_xref", "customer_source",
                "customer_document", "customer")) {
            db.update("delete from " + table);
        }
    }

    @Test
    void theSameCustomerIsFoundByItsDniAndByItsPassport() throws Exception {
        var ana = resolve("D1", person("Ana", "Ruiz", "ana@example.com", "ES", null, "DNI", "12345678-Z")).get(0).customerId();
        addDocument(ana, """
                {"type":"PASSPORT","number":"PAA 123456","issuingCountry":"ES","expiry":"2031-05-01","origin":"CUSTOMER"}""");

        found("documentNumber", "12345678Z").andExpect(jsonPath("$.customerId").value(ana))
                .andExpect(jsonPath("$.matchedBy").value("DOCUMENT"))
                .andExpect(jsonPath("$.firstName").value("Ana"));
        mvc.perform(get("/identities/lookup").param("documentNumber", "paa123456").param("country", "es"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(ana));
        // The main document is still the DNI; the passport is one more.
        assertThat(customers.findById(ana).orElseThrow().documentNumber).isEqualTo("12345678-Z");
        assertThat(documents.of(ana)).extracting(d -> d.type).containsExactly("DNI", "PASSPORT");

        // A reservation with the passport is her, too.
        assertThat(resolve("D2", person("Ana", "Ruiz", null, "ES", null, "PASSPORT", "PAA123456")).get(0))
                .extracting(ResolvedIdentity::customerId, ResolvedIdentity::matchedBy).containsExactly(ana, "DOCUMENT");
    }

    @Test
    void theSameNumberReadAsAnotherTypeIsTheSameDocument() throws Exception {
        var leo = resolve("D3", person("Leo", "Mas", null, "DE", null, "DOC", "C01X00T47")).get(0).customerId();
        assertThat(documents.of(leo)).singleElement().satisfies(d -> {
            assertThat(d.type).isEqualTo("OTHER");
            assertThat(d.issuingCountry).isEqualTo("DE");
        });

        commands.handle(scan(leo, null, "Leo", "Mas", "PASSPORT", "C01X00T47", "DE", null));

        // Still one document — now known to be a passport — and nothing to propose.
        assertThat(documents.of(leo)).singleElement().satisfies(d -> assertThat(d.type).isEqualTo("PASSPORT"));
        assertThat(changeRequests.findByCustomerIdOrderByRequestedAtDesc(leo)).isEmpty();
        assertThat(resolve("D4", person("Leo", "Mas", null, "DE", null, "PASSPORT", "C01X00T47")).get(0).customerId()).isEqualTo(leo);
    }

    @Test
    void theSameNumberFromTwoCountriesIsTwoDocumentsAndWithoutTheCountryItIsAmbiguous() throws Exception {
        var es = resolve("D5", person("Juan", "Silva", "juan@example.com", "ES", null, null, null)).get(0).customerId();
        var pt = resolve("D6", person("João", "Silva", "joao@example.com", "PT", null, null, null)).get(0).customerId();
        addDocument(es, """
                {"type":"ID_CARD","number":"AB12345","issuingCountry":"ES"}""");
        addDocument(pt, """
                {"type":"ID_CARD","number":"AB12345","issuingCountry":"PT"}""");

        mvc.perform(get("/identities/lookup").param("documentNumber", "AB12345").param("country", "ES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(es));
        mvc.perform(get("/identities/lookup").param("documentNumber", "AB12345").param("country", "PT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(pt));
        mvc.perform(get("/identities/lookup").param("documentNumber", "AB12345").param("country", "FR"))
                .andExpect(status().isNotFound());
        // Without the country, two customers: the desk is told so, and nothing about either.
        mvc.perform(get("/identities/lookup").param("documentNumber", "AB-12345"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ambiguous").value(true))
                .andExpect(jsonPath("$.matchedBy").value("DOCUMENT"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.firstName").doesNotExist());
    }

    @Test
    void aDocumentTwoCustomersHoldIsAmbiguousAndAnUnknownOneIsNotFound() throws Exception {
        // Duplicates already: two provisional customers with the same document from the same country.
        var first = customer("Eva", "Puig", "ES", "77777777B");
        var second = customer("Eva", "Puig", "ES", "77777777B");
        migration.migrate();
        assertThat(first).isNotEqualTo(second);

        mvc.perform(get("/identities/lookup").param("documentNumber", "77777777B").param("country", "ES"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.count").value(2));
        mvc.perform(get("/identities/lookup").param("documentNumber", "00000000T")).andExpect(status().isNotFound());
        // Merged, they are one: the survivor answers.
        db.update("update customer set status = 'MERGED', alias_of = ? where id = ?", first, second);
        db.update("update customer set status = 'CONSOLIDATED' where id = ?", first);
        found("documentNumber", "77777777B").andExpect(jsonPath("$.customerId").value(first))
                .andExpect(jsonPath("$.status").value("CONSOLIDATED"));
    }

    @Test
    void exactlyOneKeyIsAskedFor() throws Exception {
        mvc.perform(get("/identities/lookup")).andExpect(status().isBadRequest());
        mvc.perform(get("/identities/lookup").param("documentNumber", "1").param("email", "a@b.c")).andExpect(status().isBadRequest());
        mvc.perform(get("/identities/lookup").param("country", "ES")).andExpect(status().isBadRequest());

        var ana = resolve("D7", person("Ana", "Gil", "Ana.Gil@example.com", "ES", null, null, null)).get(0).customerId();
        found("email", " ana.gil@EXAMPLE.com ").andExpect(jsonPath("$.customerId").value(ana))
                .andExpect(jsonPath("$.matchedBy").value("EMAIL"));
    }

    @Test
    void aCandidateIsFoundByNameAndBirthDateAndNoneWithoutTheBirthDate() throws Exception {
        var born = LocalDate.of(1980, 1, 15);
        var jose = resolve("D8", person("José", "Pérez", "jose@example.com", "ES", born, null, null)).get(0).customerId();
        var other = resolve("D9", person("Jose", "Perez", "jose@example.fr", "FR", born, null, null)).get(0).customerId();
        resolve("D10", person("Josefa", "Pérez", "josefa@example.com", "ES", born, null, null));
        resolve("D11", person("José", "Pérez", "jose.p@example.com", "ES", LocalDate.of(1981, 1, 15), null, null));

        mvc.perform(get("/identities/candidates").param("firstName", "JOSE").param("lastName", "perez")
                        .param("birthDate", "1980-01-15").param("nationality", "ES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].customerId").value(jose))
                .andExpect(jsonPath("$[0].matched").value(org.hamcrest.Matchers.contains("NAME", "BIRTH_DATE", "NATIONALITY")))
                .andExpect(jsonPath("$[0].nationality").value("ES"))
                .andExpect(jsonPath("$[1].customerId").value(other))
                .andExpect(jsonPath("$[1].matched").value(org.hamcrest.Matchers.contains("NAME", "BIRTH_DATE")));
        mvc.perform(get("/identities/candidates").param("firstName", "José").param("lastName", "Pérez"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/identities/candidates").param("firstName", "José").param("lastName", "Pérez").param("birthDate", "15/01/1980"))
                .andExpect(status().isBadRequest());
        // Nothing was merged.
        assertThat(customers.findById(other).orElseThrow().status).isEqualTo(CustomerStatus.PROVISIONAL);
    }

    @Test
    void aCustomersMainDocumentFromBeforeIsMigratedOnce() {
        var id = customer("Pau", "Roig", "ES", "11.111.111-H");
        var merged = customer("Pau", "Roig", "ES", "22222222J");
        db.update("update customer set status = 'MERGED', alias_of = ? where id = ?", id, merged);

        assertThat(migration.migrate()).isEqualTo(1);
        assertThat(documents.of(id)).singleElement().satisfies(d -> {
            assertThat(d.numberKey).isEqualTo("11111111H");
            assertThat(d.number).isEqualTo("11.111.111-H");
            assertThat(d.type).isEqualTo("DNI");
            assertThat(d.issuingCountry).isEqualTo("ES");
            assertThat(d.origin).isEqualTo("CUSTOMER");
        });
        assertThat(documents.of(merged)).isEmpty();

        // On the next start, nothing to do.
        assertThat(migration.migrate()).isZero();
        assertThat(documents.of(id)).hasSize(1);
    }

    @Test
    void aScannedDocumentNobodyHoldsIsAddedBesideTheMainOne() throws Exception {
        var ana = resolve("D12", person("Ana", "Ruiz", "ana@example.com", "ES", null, "DNI", "12345678Z")).get(0).customerId();
        db.update("delete from outbox_message");

        commands.handle(scan(ana, null, "Ana", "Ruiz", "PASSPORT", "PAB765432", "ES", LocalDate.of(2032, 1, 31)));

        var customer = customers.findById(ana).orElseThrow();
        assertThat(customer.documentNumber).isEqualTo("12345678Z");
        assertThat(documents.of(ana)).extracting(d -> d.type + ":" + d.number + ":" + d.issuingCountry + ":" + d.origin)
                .containsExactly("DNI:12345678Z:ES:RESERVATION", "PASSPORT:PAB765432:ES:SCAN");
        assertThat(documents.of(ana).get(1).expiry).isEqualTo(LocalDate.of(2032, 1, 31));
        // Not a contradiction: no change request; and Salesforce, which keeps the main one, is not asked.
        assertThat(changeRequests.findByCustomerIdOrderByRequestedAtDesc(ana)).isEmpty();
        // The hotels learn it: the golden record carries both documents.
        assertThat(db.queryForList("select payload from outbox_message", String.class)).singleElement().asString()
                .contains("\"documents\":[{\"type\":\"DNI\",\"number\":\"12345678Z\",\"issuingCountry\":\"ES\"},"
                        + "{\"type\":\"PASSPORT\",\"number\":\"PAB765432\",\"issuingCountry\":\"ES\"}]");
    }

    @Test
    void theDeskConfirmingAKnownCustomerConsolidatesThePaxIntoItAndTheDocumentJoinsIts() throws Exception {
        var known = resolve("D13", person("Marta", "Soler", "marta@example.com", "ES", null, "DNI", "33333333P")).get(0).customerId();
        mvc.perform(put("/customers/" + known + "/xrefs").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"target":"RIU_CLASS","reference":" rc-0042 "}""")).andExpect(status().isOk());
        // She comes with another email and nothing certain: a provisional customer.
        var pax = resolve("D14", person("Marta", "Soler", "m.soler@work.example.com", "ES", null, null, null)).get(0).customerId();
        assertThat(pax).isNotEqualTo(known);

        // The desk found her by her Riu Class card, and scans her passport.
        found("riuClass", "RC-0042").andExpect(jsonPath("$.customerId").value(known))
                .andExpect(jsonPath("$.matchedBy").value("RIU_CLASS"));
        commands.handle(scan(pax, known, "Marta", "Soler", "PASSPORT", "PAC111222", null, null));

        var absorbed = customers.findById(pax).orElseThrow();
        assertThat(absorbed.status).isEqualTo(CustomerStatus.MERGED);
        assertThat(absorbed.aliasOf).isEqualTo(known);
        assertThat(customers.findById(known).orElseThrow().status).isEqualTo(CustomerStatus.CONSOLIDATED);
        assertThat(consolidations.findById(pax).orElseThrow().via).isEqualTo("DESK_CONFIRMED");
        assertThat(documents.of(known)).extracting(d -> d.numberKey).containsExactly("33333333P", "PAC111222");
        found("documentNumber", "PAC111222").andExpect(jsonPath("$.customerId").value(known));
        // Her provisional code answers with her, and so does the card.
        mvc.perform(get("/customers").param("xref", "RIU_CLASS:rc-0042")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(known));
    }

    @Test
    void aDocumentAddedToACustomerWithoutOneIsItsMainOne() throws Exception {
        var leo = resolve("D15", person("Leo", "Vidal", "leo@example.com", "ES", null, null, null)).get(0).customerId();
        db.update("update customer set salesforce_state = 'PROJECTED' where id = ?", leo);

        addDocument(leo, """
                {"type":"DNI","number":"44444444A"}""");

        var customer = customers.findById(leo).orElseThrow();
        assertThat(customer.documentNumber).isEqualTo("44444444A");
        assertThat(customer.documentKey).isEqualTo("DNI:44444444A");
        assertThat(customer.salesforceState).isEqualTo(SalesforceState.PENDING);
        assertThat(documents.of(leo)).singleElement().satisfies(d -> assertThat(d.issuingCountry).isEqualTo("ES"));
        mvc.perform(post("/customers/" + leo + "/documents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DNI\",\"number\":\" \"}")).andExpect(status().isBadRequest());
    }

    @Test
    void aSecondDocumentSendsTheContactAgain_itListsThemAll() throws Exception {
        var leo = resolve("D16", person("Leo", "Vidal", "leo2@example.com", "ES", null, "DNI", "55555555K")).get(0).customerId();
        db.update("update customer set salesforce_state = 'PROJECTED' where id = ?", leo);

        addDocument(leo, """
                {"type":"PASSPORT","number":"PAX555","issuingCountry":"ESP"}""");

        var customer = customers.findById(leo).orElseThrow();
        assertThat(customer.documentNumber).isEqualTo("55555555K");
        assertThat(customer.salesforceState).isEqualTo(SalesforceState.PENDING);
        customer.documents = documents.of(leo);
        assertThat(io.mateu.ecdemo1.mdm.salesforce.SalesforceClientFields.contact(customer).get("Documentos__c"))
                .asString().contains("principal").contains("Pasaporte · ESP · PAX555");
    }

    org.springframework.test.web.servlet.ResultActions found(String param, String value) throws Exception {
        return mvc.perform(get("/identities/lookup").param(param, value)).andExpect(status().isOk());
    }

    void addDocument(String customerId, String body) throws Exception {
        mvc.perform(post("/customers/" + customerId + "/documents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    /** A customer as one from before documents were kept apart: only its main document. */
    String customer(String first, String last, String nationality, String dni) {
        var c = new Customer();
        c.id = "C-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        c.status = CustomerStatus.PROVISIONAL;
        c.firstName = first;
        c.lastName = last;
        c.nationality = nationality;
        c.documentType = "DNI";
        c.documentNumber = dni;
        c.documentKey = io.mateu.ecdemo1.mdm.resolution.Normalizer.document("DNI", dni);
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        c.salesforceState = SalesforceState.PENDING;
        return customers.save(c).id;
    }

    static Person person(String first, String last, String email, String nationality, LocalDate birthDate, String docType, String doc) {
        return new Person(first, last, GuestType.ADULT, null, email, null, nationality, birthDate, docType, doc);
    }

    static RecordScannedIdentity scan(String customerId, String confirmedCustomerId, String first, String last, String type,
                                      String number, String issuingCountry, LocalDate expiry) {
        return new RecordScannedIdentity(UUID.randomUUID().toString(), "PMI01", null, null, 1, customerId, first, last, type,
                number, null, "ES", "front office PMI01", issuingCountry, expiry, confirmedCustomerId);
    }

    List<ResolvedIdentity> resolve(String locator, Person... passengers) throws Exception {
        var answer = mvc.perform(post("/identities/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new IdentityRequest("PMI01", locator, List.of(passengers)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return List.of(json.readValue(answer, ResolvedIdentity[].class));
    }
}
