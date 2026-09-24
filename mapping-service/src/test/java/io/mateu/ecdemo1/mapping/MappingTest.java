package io.mateu.ecdemo1.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.store.CauseRecordRepository;
import io.mateu.ecdemo1.mapping.store.CauseStatus;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import io.mateu.ecdemo1.mapping.store.WaiterRepository;
import io.mateu.ecdemo1.mapping.store.WaiterStatus;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The mapping against a real Postgres and broker, with the CRS adapter played by a small HTTP
 * server. Steps are driven the way the engine drives them — a task on the mapping topic — and
 * observed the way the engine would see them: replies and messages on upstream.
 */
@SpringBootTest(properties = {"mapping.resend-after=2s", "mapping.resend-check=1s"})
@AutoConfigureMockMvc
@Testcontainers
class MappingTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7")
            .withTmpFs(Map.of("/var/lib/redpanda/data", "rw"));

    static HttpServer crs;
    /** What the integrations service answers for PMI01's integration; null for "no integration". */
    static volatile String integrationStatus = "ACTIVE";

    static String reservation(String locator, String partner) {
        return """
                {"hotelCode":"PMI01","locator":"%s","version":2,"status":"CONFIRMED","channelCode":"WEB",
                 "partnerCode":%s,"arrival":"2026-10-05","departure":"2026-10-07","currency":"EUR",
                 "holder":{"firstName":"Ana","lastName":"García","type":"ADULT"},
                 "rooms":[{"line":1,"roomTypeCode":"DBL","ratePlanCode":"BAR","boardCode":"AD","adults":2,
                           "childrenAges":[],"guests":[],"nightlyRates":[{"date":"2026-10-05","amount":135}]}],
                 "payments":[],"totalAmount":135}""".formatted(locator, partner == null ? "null" : "\"" + partner + "\"");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        crs = HttpServer.create(new InetSocketAddress(0), 0);
        crs.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            String body = path.startsWith("/reservations/PMI01/")
                    ? reservation(parts[3], parts[3].startsWith("P-") ? "NORDTRAVEL" : null)
                    : path.equals("/integrations/hotels/PMI01") && integrationStatus != null
                    ? """
                      {"id":"I-1","crsHotelCode":"PMI01","pmsHotelCode":"RIUPMI","status":"%s"}""".formatted(integrationStatus)
                    : null;
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
        crs.start();
        registry.add("CRS_INTEGRATION_URL", () -> "http://localhost:" + crs.getAddress().getPort());
        registry.add("INTEGRATIONS_URL", () -> "http://localhost:" + crs.getAddress().getPort());
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
    }

    @AfterAll
    static void stop() {
        crs.stop(0);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    Dictionary dictionary;
    @Autowired
    MappingEntryRepository entries;
    @Autowired
    CauseRecordRepository causes;
    @Autowired
    WaiterRepository waiters;

    @Test
    void aReservationMissingCodesWaitsOnAllOfThemAndResumesOnlyWhenTheLastIsApproved() throws Exception {
        var key = "proyectar-reserva:PMI01/L1:E1";
        var reply = runStep("prepare-reservation", "L1", key);
        assertThat(reply.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(variables(reply)).containsEntry("prepareOutcome", "WAIT");

        assertThat(causes.findAll()).extracting(c -> c.causeKey).contains(
                "MISSING_MAPPING:PMI01:HOTEL:PMI01", "MISSING_MAPPING:PMI01:CHANNEL:WEB",
                "MISSING_MAPPING:PMI01:ROOM_TYPE:DBL", "MISSING_MAPPING:PMI01:RATE_PLAN:BAR",
                "MISSING_MAPPING:PMI01:BOARD:AD");
        var expected = List.of("HOTEL:PMI01", "CHANNEL:WEB", "ROOM_TYPE:DBL", "RATE_PLAN:BAR", "BOARD:AD");
        assertThat(consume("notifications", r -> r.value().contains("CAUSE_OPENED")
                && expected.stream().anyMatch(c -> r.value().contains("MISSING_MAPPING:PMI01:" + c)), 6, 10))
                .hasSize(5);

        define(CodeType.HOTEL, null, "PMI01", "OPERA-H1", Map.of());
        define(CodeType.CHANNEL, null, "WEB", "WEB", Map.of("marketCode", "LEIS"));
        define(CodeType.ROOM_TYPE, "PMI01", "DBL", "DBLK", Map.of());
        define(CodeType.RATE_PLAN, null, "BAR", "RACK", Map.of());
        assertThat(waiters.findById(key)).get().extracting(w -> w.status).isEqualTo(WaiterStatus.WAITING);
        define(CodeType.BOARD, null, "AD", "BB", Map.of());

        assertThat(waiters.findById(key)).get().extracting(w -> w.status).isEqualTo(WaiterStatus.RELEASED);
        // At least once: this class resends every two seconds until the process answers.
        assertThat(consume("upstream", r -> r.value().contains("causes-resolved") && r.value().contains(key), 1, 15))
                .isNotEmpty().allSatisfy(r -> assertThat(json(r.value()).get("type").asText()).isEqualTo("message-received"));

        // The process resumes and asks for its successor — twice, as a retried step would.
        runStep("relaunch", "L1", key);
        runStep("relaunch", "L1", key);
        var successors = consume("upstream", r -> r.value().contains("process-creation-requested")
                && r.value().contains(key + ">r"), 2, 8);
        assertThat(successors).singleElement().satisfies(r -> {
            var start = json(r.value());
            assertThat(start.get("workflowDefinitionId").asText()).isEqualTo("proyectar-reserva");
            assertThat(variables(start)).containsEntry("processKey", key + ">r").containsEntry("locator", "L1")
                    .doesNotContainKey("prepareOutcome");
        });

        // The successor finds everything mapped.
        assertThat(variables(runStep("prepare-reservation", "L1", key + ">r"))).containsEntry("prepareOutcome", "OK");
    }

    @Test
    void theResumeMessageIsSentAgainUntilTheProcessAnswers() throws Exception {
        var key = "proyectar-reserva:PMI01/L2:E2";
        var cause = "MISSING_MAPPING:PMI01:BOARD:ZZ";
        dictionary.propose(new Dictionary.Proposal(CodeType.BOARD, null, "ZZ", "X", Map.of(), null, null), "t");
        waitOn(key, cause);
        approveProposalFor(CodeType.BOARD, "ZZ");

        var signals = consume("upstream", r -> r.value().contains("causes-resolved") && r.value().contains(key), 2, 10);
        assertThat(signals.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void aPropertyExceptionWinsOverTheChainAndApprovingSupersedesThePreviousVersion() {
        define(CodeType.RATE_PLAN, null, "NRF", "NONREF", Map.of());
        define(CodeType.RATE_PLAN, "CUN01", "NRF", "NRF-MX", Map.of());
        assertThat(dictionary.resolve("PMI01", CodeType.RATE_PLAN, "NRF")).get().extracting(t -> t.targetCode()).isEqualTo("NONREF");
        assertThat(dictionary.resolve("CUN01", CodeType.RATE_PLAN, "NRF")).get().extracting(t -> t.targetCode()).isEqualTo("NRF-MX");

        define(CodeType.RATE_PLAN, null, "NRF", "NONREF2", Map.of());
        assertThat(dictionary.resolve("PMI01", CodeType.RATE_PLAN, "NRF")).get().extracting(t -> t.targetCode()).isEqualTo("NONREF2");
        assertThat(entries.findAll()).filteredOn(e -> e.sourceCode.equals("NRF") && e.hotelCode == null)
                .extracting(e -> e.status + "@" + e.entryVersion)
                .containsExactlyInAnyOrder("SUPERSEDED@1", "APPROVED@2");
    }

    @Test
    void aMissingPartnerBlocksUntilItsProfileIsRecorded() throws Exception {
        for (var type : List.of(CodeType.HOTEL, CodeType.CHANNEL, CodeType.ROOM_TYPE, CodeType.RATE_PLAN, CodeType.BOARD)) {
            var code = switch (type) { case HOTEL -> "PMI01"; case CHANNEL -> "WEB"; case ROOM_TYPE -> "DBL"; case RATE_PLAN -> "BAR"; default -> "AD"; };
            if (dictionary.resolve("PMI01", type, code).isEmpty()) {
                define(type, type == CodeType.ROOM_TYPE ? "PMI01" : null, code, "X-" + code, Map.of());
            }
        }
        var key = "proyectar-reserva:PMI01/P-1:E3";
        assertThat(variables(runStep("prepare-reservation", "P-1", key))).containsEntry("prepareOutcome", "WAIT");
        assertThat(causes.findById("MISSING_PARTNER:NORDTRAVEL")).get().extracting(c -> c.status).isEqualTo(CauseStatus.OPEN);

        runStepWith("record-partner-profile", "proyectar-interlocutor:partner/NORDTRAVEL:E9", List.of(
                new Variable("partnerCode", "NORDTRAVEL"), new Variable("pmsProfileIds", "TA-100"),
                new Variable("pmsProfileType", "TRAVEL_AGENT"), new Variable("version", "1")));

        assertThat(causes.findById("MISSING_PARTNER:NORDTRAVEL")).get().extracting(c -> c.status).isEqualTo(CauseStatus.RESOLVED);
        assertThat(waiters.findById(key)).get().extracting(w -> w.status).isEqualTo(WaiterStatus.RELEASED);
    }

    @Test
    void anAgentProposalIsNotInForceUntilAPersonApprovesIt() {
        var proposal = dictionary.propose(new Dictionary.Proposal(CodeType.PAYMENT_METHOD, null, "AMEX", "AX",
                Map.of(), 0.9, "American Express is AX in the PMS"), "agent");
        assertThat(dictionary.resolve("PMI01", CodeType.PAYMENT_METHOD, "AMEX")).isEmpty();
        dictionary.approve(proposal.getId(), "Miguel");
        assertThat(dictionary.resolve("PMI01", CodeType.PAYMENT_METHOD, "AMEX")).get().extracting(t -> t.targetCode()).isEqualTo("AX");
        assertThat(entries.findById(proposal.getId())).get().satisfies(e -> {
            assertThat(e.status).isEqualTo(EntryStatus.APPROVED);
            assertThat(e.proposedBy).isEqualTo("agent");
            assertThat(e.decidedBy).isEqualTo("Miguel");
        });
    }

    @Autowired
    io.mateu.ecdemo1.mapping.causes.Causes causesService;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void resolvingACauseAndDecidingTheLastProposalCloseWhatTheyAnnounced() throws Exception {
        jdbc.update("delete from outbox_message where binding = 'resolutions'");
        var entry = dictionary.propose(new Dictionary.Proposal(CodeType.ROOM_TYPE, "RES01", "RESX", "XRES", Map.of(), null, null), "agent");
        entries.findByStatusOrderByCreatedAtDesc(io.mateu.ecdemo1.mapping.store.EntryStatus.PROPOSED).stream()
                .filter(e -> !e.id.equals(entry.id)).forEach(e -> dictionary.reject(e.id, "cleanup"));
        jdbc.update("delete from outbox_message where binding = 'resolutions'");
        dictionary.approve(entry.id, "Ana García");

        var subjects = jdbc.queryForList("select message_key from outbox_message where binding = 'resolutions' order by seq", String.class);
        // The approval resolves the cause of the code, if there was one, and it was the last proposal.
        assertThat(subjects).contains(io.mateu.ecdemo1.mapping.proposals.ProposalAnnouncer.SUBJECT);
        var payload = jdbc.queryForObject("select payload from outbox_message where binding = 'resolutions' and message_key = ? order by seq desc limit 1",
                String.class, io.mateu.ecdemo1.mapping.proposals.ProposalAnnouncer.SUBJECT);
        assertThat(objectMapper.readValue(payload, io.mateu.ecdemo1.integration.model.notification.NotificationResolved.class).resolvedBy())
                .isEqualTo("Ana García");
    }

    @Test
    void anEquivalenceInForceCanBeWithdrawnAndTheCodeHasNoneAgain() throws Exception {
        var entry = dictionary.define(new Dictionary.Proposal(CodeType.BOARD, "WDR01", "WX", "SA", Map.of(), null, null), "Ana García");
        assertThat(dictionary.resolve("WDR01", CodeType.BOARD, "WX")).isPresent();
        jdbc.update("delete from outbox_message where binding = 'audit'");

        var withdrawn = dictionary.withdraw(entry.id, "Luis Pons");

        assertThat(withdrawn.status).isEqualTo(io.mateu.ecdemo1.mapping.store.EntryStatus.WITHDRAWN);
        assertThat(withdrawn.decidedBy).isEqualTo("Luis Pons");
        assertThat(dictionary.resolve("WDR01", CodeType.BOARD, "WX")).isEmpty();
        // Only what is in force can be withdrawn — and the refusal is audited too.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dictionary.withdraw(entry.id, "Luis Pons"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("WITHDRAWN");
        var audited = jdbc.queryForList("select payload from outbox_message where binding = 'audit' order by seq", String.class);
        assertThat(audited).hasSize(2).allMatch(p -> p.contains("\"action\":\"Withdraw mapping\""));
        assertThat(audited.get(0)).contains("\"succeeded\":true").contains("Luis Pons");
        assertThat(audited.get(1)).contains("\"succeeded\":false");
    }

    @Test
    void mappingDecisionsAreAuditedAndARefusalToo() throws Exception {
        jdbc.update("delete from outbox_message where binding = 'audit'");
        var entry = dictionary.propose(new Dictionary.Proposal(CodeType.ROOM_TYPE, "AUD01", "AUDX", "XAUD", Map.of(), null, null), "agent");
        dictionary.approve(entry.id, "Ana García");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dictionary.reject(entry.id, "Luis"))
                .isInstanceOf(IllegalStateException.class);

        var audited = new java.util.ArrayList<io.mateu.ecdemo1.integration.model.audit.AuditedAction>();
        for (var payload : jdbc.queryForList("select payload from outbox_message where binding = 'audit' order by seq", String.class)) {
            audited.add(objectMapper.readValue(payload, io.mateu.ecdemo1.integration.model.audit.AuditedAction.class));
        }
        assertThat(audited).extracting(a -> a.action(), a -> a.by(), a -> a.hotelCode(), a -> a.succeeded())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Propose mapping", "agent", "AUD01", true),
                        org.assertj.core.groups.Tuple.tuple("Approve mapping", "Ana García", "AUD01", true),
                        org.assertj.core.groups.Tuple.tuple("Reject mapping", "Luis", "AUD01", false));
        assertThat(audited.get(1).response()).contains("AUDX → XAUD: APPROVED (v1)");
        assertThat(audited).extracting(a -> a.service()).containsOnly("mapping");
    }

    @Autowired
    org.springframework.ai.tool.ToolCallbackProvider mappingTools;

    @Autowired
    io.mateu.ecdemo1.mapping.mcp.MappingMcpTools tools;

    @Test
    void theMcpServerOffersEveryToolTheAgentNeeds() {
        // Spring AI drops a tool it cannot describe — one returning Object — with only a warning at
        // startup. This is where that would show.
        assertThat(java.util.Arrays.stream(mappingTools.getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .contains("listOpenCauses", "listPendingCodes", "getPmsCatalog", "proposeMappings", "listProposals",
                        "approveMapping", "rejectMapping", "resolveCause");
    }

    @Test
    void theAgentsProposalsWaitForAPersonAndAnApprovalNeedsAName() {
        var answer = tools.proposeMappings(List.of(
                new Dictionary.Proposal(CodeType.BOARD, null, "MP", "HALFB", Map.of(), 0.95, "Media pensión is half board"),
                new Dictionary.Proposal(CodeType.BOARD, null, "TI", "ALLINC", Map.of(), 0.9, "Todo incluido is all inclusive")));
        assertThat(answer).startsWith("Proposed 2");
        assertThat(dictionary.resolve("PMI01", CodeType.BOARD, "MP")).isEmpty();
        var mp = tools.listProposals().stream().filter(p -> p.sourceCode().equals("MP")).findFirst().orElseThrow();
        assertThat(tools.approveMapping(mp.id(), "agent")).startsWith("Error");
        assertThat(tools.approveMapping(mp.id(), "Miguel")).startsWith("Approved BOARD MP → HALFB");
        assertThat(dictionary.resolve("PMI01", CodeType.BOARD, "MP")).isPresent();
        assertThat(consume("notifications", r -> r.value().contains("PROPOSAL_READY"), 1, 10)).isNotEmpty();
    }

    void waitOn(String processKey, String causeKey) {
        causesService.await(processKey, "proyectar-reserva", "PMI01", "L2",
                List.of(new Variable("locator", "L2"), new Variable("hotelCode", "PMI01")),
                List.of(new io.mateu.ecdemo1.integration.model.mapping.Cause(causeKey,
                        io.mateu.ecdemo1.integration.model.mapping.CauseType.MISSING_MAPPING, "test")));
    }

    void approveProposalFor(CodeType type, String code) {
        entries.findAll().stream().filter(e -> e.type == type && e.sourceCode.equals(code) && e.status == EntryStatus.PROPOSED)
                .forEach(e -> dictionary.approve(e.id, "t"));
    }

    void define(CodeType type, String hotel, String code, String target, Map<String, String> attributes) {
        dictionary.define(new Dictionary.Proposal(type, hotel, code, target, attributes, null, null), "test");
    }

    @Test
    void enteringAnEquivalenceAlreadyInForceFromTheChainChangesNothing() throws Exception {
        define(CodeType.HOTEL, null, "MRU01", "XMAR", Map.of());
        var before = entries.count();

        // The integration enters the hotel's own equivalence; the chain's already says the same.
        mvc.perform(post("/entries/definitions").param("by", "integration MRU01").contentType("application/json")
                        .content("""
                                {"type":"HOTEL","hotelCode":"MRU01","sourceCode":"MRU01","targetCode":"XMAR","attributes":{}}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetCode").value("XMAR"));
        assertThat(entries.count()).isEqualTo(before);
    }

    @Test
    void aHotelWhoseIntegrationIsNotActiveHoldsItsReservationsButNotItsBackfill() throws Exception {
        define(CodeType.HOTEL, null, "PMI01", "OPERA-H1", Map.of());
        define(CodeType.CHANNEL, null, "WEB", "WEB", Map.of("marketCode", "LEIS"));
        define(CodeType.ROOM_TYPE, "PMI01", "DBL", "DBLK", Map.of());
        define(CodeType.RATE_PLAN, null, "BAR", "RACK", Map.of());
        define(CodeType.BOARD, null, "AD", "BB", Map.of());
        integrationStatus = "BACKFILLING";
        try {
            var live = "proyectar-reserva:PMI01/L7:E7";
            assertThat(variables(runStep("prepare-reservation", "L7", live))).containsEntry("prepareOutcome", "WAIT");
            assertThat(causes.findById("INTEGRATION_INACTIVE:PMI01")).get()
                    .satisfies(c -> assertThat(c.status).isEqualTo(CauseStatus.OPEN));

            var backfill = "proyectar-reserva:PMI01/L8:backfill:R1";
            assertThat(variables(runStepWith("prepare", backfill,
                    List.of(new Variable("locator", "L8"), new Variable("origin", "backfill:R1")))))
                    .containsEntry("prepareOutcome", "OK");

            integrationStatus = "ACTIVE";
            mvc.perform(post("/causes/resolve-if-open").param("key", "INTEGRATION_INACTIVE:PMI01").param("by", "integration"))
                    .andExpect(status().isOk());
            assertThat(waiters.findById(live)).get().extracting(w -> w.status).isEqualTo(WaiterStatus.RELEASED);
        } finally {
            integrationStatus = "ACTIVE";
        }
    }

    @Test
    void theBackfillsPrePassListsWhatItsReservationsLackMostBlockingFirst() throws Exception {
        define(CodeType.ROOM_TYPE, "PMI01", "DBL", "DBLK", Map.of());
        var gaps = json(mvc.perform(post("/gaps").contentType(MediaType.APPLICATION_JSON).content("""
                        {"hotelCode":"PMI01","reservations":12,
                         "codes":[{"type":"ROOM_TYPE","code":"DBL","reservations":12},
                                  {"type":"BOARD","code":"XX","reservations":3},
                                  {"type":"ROOM_TYPE","code":"ZZZ","reservations":9}],
                         "partners":[{"partnerCode":"NOBODY","reservations":5}]}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var listed = new ArrayList<String>();
        gaps.forEach(g -> listed.add(g.get("kind").asText() + ":" + g.get("code").asText() + ":" + g.get("reservations").asInt()));
        assertThat(listed).containsExactly("MAPPING:ZZZ:9", "PARTNER:NOBODY:5", "MAPPING:XX:3");
    }

    JsonNode runStep(String step, String locator, String processKey) throws Exception {
        return runStepWith(step, processKey, List.of(new Variable("locator", locator)));
    }

    JsonNode runStepWith(String step, String processKey, List<Variable> extra) throws Exception {
        var variables = new ArrayList<>(List.of(new Variable("hotelCode", "PMI01"), new Variable("processKey", processKey),
                new Variable("definitionId", processKey.substring(0, processKey.indexOf(':'))), new Variable("version", "2")));
        variables.addAll(extra);
        var taskId = UUID.randomUUID().toString();
        var definition = processKey.substring(0, processKey.indexOf(':'));
        var stepId = switch (step) {
            case "prepare", "prepare-reservation", "prepare-cancellation", "prepare-partner" -> "prepare";
            case "relaunch" -> "relaunch-prepare";
            default -> step;
        };
        var task = new TaskExecutionRequested(taskId, "P-" + taskId, definition, stepId, "", variables);
        send("mapping", task.processId(), objectMapper.writerFor(DomainEvent.class).writeValueAsString(task));
        return consume("upstream", r -> r.value().contains(taskId) && r.value().contains("task-status-changed"), 1, 20)
                .stream().findFirst().map(r -> json(r.value()))
                .orElseThrow(() -> new AssertionError("No reply to " + step));
    }

    static Map<String, String> variables(JsonNode event) {
        var map = new java.util.HashMap<String, String>();
        event.get("variables").forEach(v -> map.put(v.get("name").asText(), v.get("value").asText()));
        return map;
    }

    JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void send(String topic, String key, String value) {
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers()),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static List<ConsumerRecord<String, String>> consume(String topic, Predicate<ConsumerRecord<String, String>> match,
                                                        int enough, int seconds) {
        var found = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            var deadline = Instant.now().plusSeconds(seconds);
            while (found.size() < enough && Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) {
                        found.add(record);
                    }
                }
            }
        }
        return found;
    }
}
