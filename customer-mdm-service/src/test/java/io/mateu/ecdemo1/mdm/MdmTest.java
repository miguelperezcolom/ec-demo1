package io.mateu.ecdemo1.mdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.integration.model.customer.CustomerChanged;
import io.mateu.ecdemo1.integration.model.customer.CustomerEvent;
import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import io.mateu.ecdemo1.integration.model.customer.IdentityRequest;
import io.mateu.ecdemo1.integration.model.customer.ResolvedIdentity;
import io.mateu.ecdemo1.integration.model.reservation.GuestType;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.mdm.consolidation.Consolidations;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    /** Salesforce's contacts as a query by MDM id answers them — what the master has now. */
    static final Map<String, String> contactJsonByMdmId = new ConcurrentHashMap<>();
    /** The Cases opened for change requests, by request id, and how each one was decided. */
    static final Map<String, String> cases = new ConcurrentHashMap<>();
    static final Map<String, String> decisions = new ConcurrentHashMap<>();

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
            } else if (path.startsWith("/services/data/v67.0/sobjects/Case/MdmRequestId__c/")) {
                var requestId = path.substring(path.lastIndexOf('/') + 1);
                cases.put(requestId, request);
                body = "{\"id\":\"500" + String.format("%015d", cases.size()) + "\",\"success\":true}";
            } else if (path.equals("/api/guests/C/kardex") || path.matches("/api/guests/[^/]+/kardex")) {
                body = "";
            } else if (path.equals("/services/data/v67.0/queryAll") && URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8).contains("FROM Case")) {
                var soql = URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8);
                var records = decisions.entrySet().stream().filter(e -> soql.contains("'" + e.getKey() + "'"))
                        .map(e -> "{\"MdmRequestId__c\":\"" + e.getKey() + "\",\"Decision__c\":\"" + e.getValue() + "\"}").toList();
                body = "{\"done\":true,\"records\":[" + String.join(",", records) + "]}";
            } else if (path.equals("/services/data/v67.0/queryAll") && URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8).contains("WHERE MDM_Id__c = '")) {
                var soql = URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8);
                var mdmId = soql.replaceAll(".*WHERE MDM_Id__c = '([^']+)'.*", "$1");
                var record = contactJsonByMdmId.get(mdmId);
                body = "{\"done\":true,\"records\":[" + (record == null ? "" : record) + "]}";
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
        // No broker here: the events are read from the outbox, where they are written, and the relay
        // that would publish them is kept from running.
        registry.add("outbox.interval", () -> "1h");
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
    CustomerRepository customers;
    @Autowired
    SourceRepository sources;
    @Autowired
    ConsolidationRepository consolidationRecords;
    @Autowired
    io.mateu.ecdemo1.mdm.change.ChangeRequests changeRequests;
    @Autowired
    io.mateu.ecdemo1.mdm.change.SalesforceProjection salesforceProjection;
    @Autowired
    io.mateu.ecdemo1.mdm.outbox.OutboxMessageRepository outboxMessages;
    @Autowired
    io.mateu.ecdemo1.mdm.change.SalesforceInbox salesforceInbox;

    @Test
    void anApprovalArrivingTwiceAtOnceIsProjectedOnce() throws Exception {
        outboxMessages.deleteAll();
        var eva = resolve("L11", person("Eva", "Soler", "eva@example.com", null, null)).get(0).customerId();
        projection.projectPending();
        var contactId = contactByMdmId.get(eva);
        contactJsonByMdmId.put(eva, contactJson(contactId, "Eva", "Soler", "eva@example.com"));
        var requestId = json.readTree(propose(eva, """
                {"email":"eva.soler@example.com","origin":"front office MRU01"}""")).path("id").asText();
        changeRequests.send();
        var before = customers.findById(eva).orElseThrow().version;

        // Approved: the contact changed and the decision was announced — both events at the same moment.
        contactJsonByMdmId.put(eva, contactJson(contactId, "Eva", "Soler", "eva.soler@example.com"));
        var start = new java.util.concurrent.CountDownLatch(1);
        var contact = Thread.ofVirtual().start(() -> { await(start); salesforceInbox.contactChanged(eva); });
        var decision = Thread.ofVirtual().start(() -> { await(start); salesforceInbox.decided(requestId, "Aprobada", "EVENT"); });
        start.countDown();
        contact.join();
        decision.join();

        assertThat(customers.findById(eva).orElseThrow().version).isEqualTo(before + 1);
        assertThat(changeRequests.get(requestId).status).isEqualTo("APPROVED");
        // Whichever of the two lands first, the new data is announced once and the decision reaches the
        // hotels — in one event, when the decision arrived first and already carried the data, or in two.
        assertThat(changes(eva)).filteredOn(CustomerChanged::dataChanged).hasSize(1);
        assertThat(changes(eva)).anyMatch(e -> "APPROVED".equals(e.decision()) && requestId.equals(e.changeRequestId()));
    }

    /** What the MDM said about a customer on the customers topic, oldest first. */
    List<CustomerEvent> events(String customerId) {
        return outboxMessages.findAll().stream()
                .filter(m -> customerId.equals(m.getMessageKey()))
                .sorted(java.util.Comparator.comparing(m -> m.getSeq()))
                .map(m -> {
                    try {
                        return json.readValue(m.getPayload(), CustomerEvent.class);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }

    List<CustomerChanged> changes(String customerId) {
        return events(customerId).stream().filter(CustomerChanged.class::isInstance).map(CustomerChanged.class::cast).toList();
    }

    static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String contactJson(String id, String first, String last, String email) {
        return """
                {"Id":"%s","MDM_Id__c":"x","FirstName":"%s","LastName":"%s","Email":"%s","Phone":null,"Birthdate":null,
                 "Nationality__c":"ES","Document_Type__c":null,"Document_Number__c":null}""".formatted(id, first, last, email);
    }

    String propose(String customerId, String body) throws Exception {
        return mvc.perform(post("/customers/" + customerId + "/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void aChangeProposedByAHotelIsDecidedInSalesforceAndTheDecisionReachesTheHotels() throws Exception {
        outboxMessages.deleteAll();
        var ana = resolve("L9", person("Ana", "García", "ana@example.com", null, null)).get(0).customerId();
        projection.projectPending();
        var contactId = contactByMdmId.get(ana);
        contactJsonByMdmId.put(ana, contactJson(contactId, "Ana", "García", "ana@example.com"));

        // Reception changes her email and her name: a proposal, not yet her data.
        var answer = json.readTree(propose(ana, """
                {"name":"Ana María García","email":"ana.maria@example.com","origin":"front office MRU01 · luis"}"""));
        var requestId = answer.path("id").asText();
        assertThat(answer.path("status").asText()).isEqualTo("PENDING");
        assertThat(answer.path("changes").asText()).contains("email ana@example.com → ana.maria@example.com").contains("nombre Ana → Ana María");
        assertThat(customers.findById(ana).orElseThrow().email).isEqualTo("ana@example.com");

        // Opened in Salesforce as a Case on her contact, waiting for a decision.
        changeRequests.send();
        assertThat(cases.get(requestId)).contains("\"ContactId\":\"" + contactId + "\"").contains("\"Decision__c\":\"Pendiente\"")
                .contains("\"Email__c\":\"ana.maria@example.com\"").contains("\"Nombre__c\":\"Ana María\"");

        // Approved in Salesforce: the flow put it on the contact. The MDM learns it (here, by asking).
        contactJsonByMdmId.put(ana, contactJson(contactId, "Ana María", "García", "ana.maria@example.com"));
        decisions.put(requestId, "Aprobada");
        salesforceInbox.poll();
        var projected = customers.findById(ana).orElseThrow();
        assertThat(projected.email).isEqualTo("ana.maria@example.com");
        assertThat(projected.firstName).isEqualTo("Ana María");
        assertThat(changeRequests.get(requestId).status).isEqualTo("APPROVED");

        // The MDM says so on the customers topic — the new data, the decision and her reservation — and calls
        // no one: the front office's kardex and Opera's profile are its subscribers' to keep.
        assertThat(changes(ana)).anyMatch(e -> "APPROVED".equals(e.decision()) && requestId.equals(e.changeRequestId())
                && e.dataChanged() && "ana.maria@example.com".equals(e.data().email()) && "Ana María García".equals(e.data().fullName())
                && e.reservations().contains("PMI01/L9"));
        assertThat(calls).noneMatch(c -> c.contains("/kardex") || c.startsWith("POST /projections"));

        // A second change, rejected: the contact stays as it was; the front office learns it, Opera is not touched.
        var rejected = json.readTree(propose(ana, """
                {"phone":"+34 600 000 000","origin":"front office MRU01 · luis"}""")).path("id").asText();
        changeRequests.send();
        decisions.put(rejected, "Rechazada");
        salesforceInbox.poll();
        assertThat(changes(ana)).anyMatch(e -> "REJECTED".equals(e.decision()) && rejected.equals(e.changeRequestId())
                && !e.dataChanged());
        assertThat(customers.findById(ana).orElseThrow().phone).isNull();
    }

    @Test
    void aContactChangedInSalesforceIsProjectedOnceAndItsEchoGoesNoFurther() throws Exception {
        outboxMessages.deleteAll();
        var leo = resolve("L10", person("Leo", "Pons", "leo@example.com", null, null)).get(0).customerId();
        projection.projectPending();
        var contactId = contactByMdmId.get(leo);
        contactJsonByMdmId.put(leo, contactJson(contactId, "Leo", "Pons Vidal", "leo@example.com"));

        assertThat(salesforceProjection.refresh(leo, null, null)).isTrue();
        assertThat(customers.findById(leo).orElseThrow().lastName).isEqualTo("Pons Vidal");
        // The same again — an echo, or the event delivered twice — changes nothing and goes nowhere.
        assertThat(salesforceProjection.refresh(leo, null, null)).isFalse();
        assertThat(changes(leo)).hasSize(1);
    }

    @Test
    void aCustomerIsFoundByWhereItIsKnownOutsideTheMdm() throws Exception {
        var ana = resolve("L20", person("Ana", "Vidal", "ana.vidal@example.com", null, null)).get(0).customerId();
        mvc.perform(put("/customers/" + ana + "/xrefs").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"OPERA","reference":"20538296","context":"XMAR/L20"}"""))
                .andExpect(status().isOk());

        mvc.perform(get("/customers").param("xref", "OPERA:20538296")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ana));
        mvc.perform(get("/customers").param("xref", "opera:99999999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/customers").param("xref", "20538296")).andExpect(status().isBadRequest());
    }

    @BeforeEach
    void clean() {
        outboxMessages.deleteAll();
        consolidationRecords.deleteAll();
        sources.deleteAll();
        customers.deleteAll();
        calls.clear();
        contacts.clear();
        contactByMdmId.clear();
        contactJsonByMdmId.clear();
        cases.clear();
        decisions.clear();
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

        // The merge is announced once, with the survivor's data and every reservation that now carries its
        // code — the absorbed customer's included; its subscribers take the code to the PMS.
        assertThat(events(survivor)).filteredOn(CustomersMerged.class::isInstance).singleElement()
                .satisfies(e -> {
                    var merged = (CustomersMerged) e;
                    assertThat(merged.absorbedId()).isEqualTo(absorbed);
                    assertThat(merged.reservations()).contains("PMI01/L1", "PMI01/L2");
                    assertThat(merged.data().documentNumber()).isEqualTo("12345678Z");
                });

        // The poll reports the same merge: it finds it done.
        consolidations.received(absorbed, absorbedContact, "POLL");
        assertThat(consolidationRecords.findById(absorbed).orElseThrow().via).isEqualTo("EVENT");
    }

    @Test
    void anotherMdmsCustomerIsIgnored() {
        // Environments share the org, and each hears every merge: one it knows nothing of is not its business.
        consolidations.received("C-NOTOURS00001", "003000000000001AAA", "EVENT");
        assertThat(consolidationRecords.count()).isZero();
        assertThat(calls).isEmpty();
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
