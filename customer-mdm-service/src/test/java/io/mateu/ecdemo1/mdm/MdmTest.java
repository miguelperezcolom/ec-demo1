package io.mateu.ecdemo1.mdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.integration.model.customer.IdentityRequest;
import io.mateu.ecdemo1.integration.model.customer.ResolvedIdentity;
import io.mateu.ecdemo1.integration.model.reservation.GuestType;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.mdm.consolidation.Consolidations;
import io.mateu.ecdemo1.mdm.consolidation.Propagation;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.salesforce.Projection;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The customer MDM against a real Postgres, with Salesforce and the CRS adapter answered by one
 * small double. The Pub/Sub subscription is off: the event's work is done by calling what it calls,
 * {@link Consolidations#received}, as the event and the poll both do.
 */
@SpringBootTest(properties = {"mdm.projection-tick=1h", "mdm.poll=1h", "mdm.propagation-tick=1h",
        "mdm.salesforce.client-id=test", "mdm.salesforce.client-secret=secret", "mdm.salesforce.subscribe=false"})
@AutoConfigureMockMvc
@Testcontainers
class MdmTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static HttpServer others;
    static final List<String> calls = new CopyOnWriteArrayList<>();
    /** The contacts the double answers queries with, by id: what Salesforce holds. */
    static final Map<String, String> contacts = new ConcurrentHashMap<>();
    static final Map<String, String> contactByMdmId = new ConcurrentHashMap<>();
    static final AtomicInteger nextContact = new AtomicInteger();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        others = HttpServer.create(new InetSocketAddress(0), 0);
        others.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            var query = exchange.getRequestURI().getRawQuery();
            var request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(exchange.getRequestMethod() + " " + path + (request.isBlank() ? "" : " " + request));
            var base = "http://localhost:" + others.getAddress().getPort();
            String body;
            int code = 200;
            if (path.equals("/services/oauth2/token")) {
                body = """
                        {"access_token":"t0k3n","instance_url":"%s","id":"%s/id/00DORG/005USER"}""".formatted(base, base);
            } else if (path.startsWith("/services/data/v67.0/sobjects/Contact/MDM_Id__c/")) {
                var mdmId = path.substring(path.lastIndexOf('/') + 1);
                var created = !contactByMdmId.containsKey(mdmId);
                var id = contactByMdmId.computeIfAbsent(mdmId, k -> "003" + String.format("%015d", nextContact.incrementAndGet()));
                code = created ? 201 : 200;
                body = "{\"id\":\"" + id + "\",\"success\":true,\"created\":" + created + "}";
            } else if (path.equals("/services/data/v67.0/queryAll")) {
                var soql = URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8);
                var id = soql.replaceAll(".*WHERE Id = '([^']+)'.*", "$1");
                var record = contacts.get(id);
                body = "{\"done\":true,\"records\":[" + (record == null ? "" : record) + "]}";
            } else if (path.equals("/projections")) {
                body = "";
            } else {
                code = 404;
                body = "{}";
            }
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        others.start();
        var base = "http://localhost:" + others.getAddress().getPort();
        registry.add("mdm.salesforce.domain", () -> base);
        registry.add("mdm.crs-integration-url", () -> base);
    }

    @AfterAll
    static void stop() {
        others.stop(0);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    IdentityResolution resolution;
    @Autowired
    Projection projection;
    @Autowired
    Consolidations consolidations;
    @Autowired
    Propagation propagation;
    @Autowired
    CustomerRepository customers;
    @Autowired
    SourceRepository sources;
    @Autowired
    ConsolidationRepository consolidationRecords;

    @BeforeEach
    void clean() {
        consolidationRecords.deleteAll();
        sources.deleteAll();
        customers.deleteAll();
        calls.clear();
        contacts.clear();
        contactByMdmId.clear();
    }

    static Person person(String first, String last, String email, String docType, String doc) {
        return new Person(first, last, GuestType.ADULT, null, email, null, "ES", null, docType, doc);
    }

    List<ResolvedIdentity> resolve(String locator, Person... passengers) throws Exception {
        var answer = mvc.perform(post("/identities/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new IdentityRequest("PMI01", locator, List.of(passengers)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return List.of(json.readValue(answer, ResolvedIdentity[].class));
    }

    @Test
    void aPassengerIsTheSameCustomerOnlyWhenItIsCertain() throws Exception {
        var ana = person("Ana", "García", "ana@example.com", null, null);
        var first = resolve("L1", ana, person("Ana", "Garcia", null, null, null), person("Leo", "García", null, null, null));
        // The holder is also a room's guest; the child is somebody else.
        assertThat(first).extracting(ResolvedIdentity::matchedBy).containsExactly("NEW", "RESERVATION", "NEW");
        assertThat(first.get(1).customerId()).isEqualTo(first.get(0).customerId());
        assertThat(first.get(0).status()).isEqualTo(CustomerStatus.PROVISIONAL);

        // Asked again — a retried step — the same customers, and nothing new.
        var again = resolve("L1", ana, person("Ana", "Garcia", null, null, null), person("Leo", "García", null, null, null));
        assertThat(again).extracting(ResolvedIdentity::customerId).isEqualTo(first.stream().map(ResolvedIdentity::customerId).toList());
        assertThat(again).extracting(ResolvedIdentity::matchedBy).containsOnly("SOURCE");
        assertThat(customers.count()).isEqualTo(2);

        // The same email and the same name, differently written: the same customer.
        assertThat(resolve("L2", person("ANA", "Garcia", " Ana@Example.com ", null, null)).get(0))
                .extracting(ResolvedIdentity::customerId, ResolvedIdentity::matchedBy)
                .containsExactly(first.get(0).customerId(), "EMAIL");
        // The same email and another name — a family's shared address — is not certain: a new customer.
        assertThat(resolve("L3", person("Pedro", "García", "ana@example.com", null, null)).get(0).matchedBy()).isEqualTo("NEW");

        // A document is certain, however it is typed.
        var withDocument = resolve("L4", person("John", "Smith", null, "PASSPORT", "X-123.456")).get(0);
        assertThat(resolve("L5", person("Jon", "Smyth", null, "passport", "x123456")).get(0))
                .extracting(ResolvedIdentity::customerId, ResolvedIdentity::matchedBy)
                .containsExactly(withDocument.customerId(), "DOCUMENT");
    }

    @Test
    void provisionalCustomersGoToSalesforceByTheirMdmId() throws Exception {
        var id = resolve("L1", person("Ana", "García", "ana@example.com", "DNI", "12345678Z")).get(0).customerId();

        projection.projectPending();

        var upsert = calls.stream().filter(c -> c.startsWith("PATCH")).toList();
        assertThat(upsert).singleElement().satisfies(c -> assertThat(c)
                .startsWith("PATCH /services/data/v67.0/sobjects/Contact/MDM_Id__c/" + id)
                .contains("\"LastName\":\"García\"", "\"Email\":\"ana@example.com\"", "\"Document_Number__c\":\"12345678Z\""));
        var customer = customers.findById(id).orElseThrow();
        assertThat(customer.salesforceState).isEqualTo(SalesforceState.PROJECTED);
        assertThat(customer.salesforceContactId).isEqualTo(contactByMdmId.get(id));

        projection.projectPending();
        assertThat(calls.stream().filter(c -> c.startsWith("PATCH"))).hasSize(1);
    }

    @Test
    void aMergeInSalesforceMakesTheAbsorbedCustomerAnAliasAndCarriesTheCodeToItsReservations() throws Exception {
        var survivor = resolve("L1", person("Ana", "García", "ana@example.com", null, null)).get(0).customerId();
        // The same person, booked with a typo in her email: nothing certain, so a second customer.
        var absorbed = resolve("L2", person("Ana", "Garcia", "ana@exmaple.com", "DNI", "12345678Z")).get(0).customerId();
        assertThat(absorbed).isNotEqualTo(survivor);
        projection.projectPending();
        var survivorContact = contactByMdmId.get(survivor);
        var absorbedContact = contactByMdmId.get(absorbed);

        // A steward merged them in Salesforce, keeping the right email and fixing the phone.
        contacts.put(absorbedContact, """
                {"Id":"%s","IsDeleted":true,"MasterRecordId":"%s","MDM_Id__c":"%s"}""".formatted(absorbedContact, survivorContact, absorbed));
        contacts.put(survivorContact, """
                {"Id":"%s","IsDeleted":false,"MasterRecordId":null,"MDM_Id__c":"%s","FirstName":"Ana","LastName":"García",
                 "Email":"ana@example.com","Phone":"+34 600 000 000"}""".formatted(survivorContact, survivor));

        consolidations.received(absorbed, absorbedContact, "EVENT");

        var s = customers.findById(survivor).orElseThrow();
        var a = customers.findById(absorbed).orElseThrow();
        assertThat(a.status).isEqualTo(CustomerStatus.MERGED);
        assertThat(a.aliasOf).isEqualTo(survivor);
        assertThat(s.status).isEqualTo(CustomerStatus.CONSOLIDATED);
        assertThat(s.phone).isEqualTo("+34 600 000 000");
        // What Salesforce did not have, the MDM keeps: the document came with the absorbed customer.
        assertThat(s.documentNumber).isEqualTo("12345678Z");
        assertThat(s.survivorship).contains("Merged " + absorbed, "phone: steward", "document: " + absorbed);

        // The absorbed code still answers, with the survivor; its reservation now resolves to it.
        mvc.perform(get("/customers/" + absorbed)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(survivor))
                .andExpect(jsonPath("$.aliases[0]").value(absorbed))
                .andExpect(jsonPath("$.reservations.length()").value(2));
        assertThat(resolve("L2", person("Ana", "Garcia", "ana@exmaple.com", "DNI", "12345678Z")).get(0).customerId()).isEqualTo(survivor);

        // The code travels to the PMS: the absorbed customer's reservation is projected again, once.
        propagation.propagate();
        propagation.propagate();
        assertThat(calls.stream().filter(c -> c.startsWith("POST /projections")).toList()).singleElement()
                .satisfies(c -> assertThat(c).contains("\"locator\":\"L2\"", "\"origin\":\"mdm-merge-" + absorbed + "\""));

        // The poll reports the same merge: it finds it done.
        consolidations.received(absorbed, absorbedContact, "POLL");
        assertThat(consolidationRecords.findById(absorbed).orElseThrow().via).isEqualTo("EVENT");
    }

    @Test
    void aContactDeletedWithoutAMergeLeavesTheCustomer() throws Exception {
        var id = resolve("L1", person("Ana", "García", "ana@example.com", null, null)).get(0).customerId();
        projection.projectPending();
        var contact = contactByMdmId.get(id);
        contacts.put(contact, """
                {"Id":"%s","IsDeleted":true,"MasterRecordId":null,"MDM_Id__c":"%s"}""".formatted(contact, id));

        consolidations.received(id, contact, "EVENT");

        var customer = customers.findById(id).orElseThrow();
        assertThat(customer.status).isEqualTo(CustomerStatus.PROVISIONAL);
        assertThat(customer.salesforceState).isEqualTo(SalesforceState.REMOVED);
        projection.projectPending();
        assertThat(calls.stream().filter(c -> c.startsWith("PATCH"))).hasSize(1);
    }
}
