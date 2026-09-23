package io.mateu.ecdemo1.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integrations.backfill.Backfill;
import io.mateu.ecdemo1.integrations.lifecycle.Gates;
import io.mateu.ecdemo1.integrations.lifecycle.Integrations;
import io.mateu.ecdemo1.integrations.store.BackfillRun;
import io.mateu.ecdemo1.integrations.store.BackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.IntegrationRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A hotel's onboarding, gate by gate, against a real Postgres and a real broker, with the CRS
 * adapter, the connector, the mapping and the master of partners answered by one small double.
 * The steps are run as the engine would run them; the gates are looked at as {@link Gates} would.
 */
@SpringBootTest(properties = {
        // A fixed test key: 32 zero bytes.
        "integrations.crypto-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        // Scheduled work off: the test looks at gates and ticks the backfill itself.
        "integrations.gate-check=1h", "integrations.backfill-tick=1h",
        // The chain's connection: what a new integration starts from when the form leaves it blank.
        "integrations.opera.gateway-url=https://ohip.example", "integrations.opera.app-key=chain-app",
        "integrations.opera.client-id=chain-client", "integrations.opera.client-secret=ch41n",
        "integrations.opera.enterprise-id=RIUE",
        "integrations.backfill-per-tick=2", "integrations.activation-window-days=10"})
@AutoConfigureMockMvc
@Testcontainers
class IntegrationsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7")
            .withTmpFs(Map.of("/var/lib/redpanda/data", "rw"));

    static HttpServer others;
    static final List<String> calls = new CopyOnWriteArrayList<>();

    // What the double answers, changed by each test as the world around the integration changes.
    static volatile boolean connectionWorks = true;
    static volatile int roomTypes = 5;
    static volatile int pendingCodes = 3;
    static volatile String gaps = "[]";
    static volatile boolean nordtravelIsAProfile = false;
    static final LocalDate TODAY = LocalDate.now();

    static String future(String query) {
        // Five reservations; the first page is two, and so on — the double pages by the cursor.
        var all = List.of("R1:1", "R2:3", "R3:8", "R4:15", "R5:40");
        var after = query == null || !query.contains("afterLocator=") ? null
                : query.replaceAll(".*afterLocator=([^&]*).*", "$1");
        var start = after == null ? 0 : all.indexOf(all.stream().filter(r -> r.startsWith(after + ":")).findFirst().orElseThrow()) + 1;
        var page = all.subList(Math.min(start, all.size()), Math.min(start + 2, all.size()));
        return "[" + String.join(",", page.stream().map(r -> {
            var parts = r.split(":");
            return """
                    {"locator":"%s","arrival":"%s","version":1}""".formatted(parts[0], TODAY.plusDays(Integer.parseInt(parts[1])));
        }).toList()) + "]";
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        others = HttpServer.create(new InetSocketAddress(0), 0);
        others.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            var query = exchange.getRequestURI().getQuery();
            var request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(exchange.getRequestMethod() + " " + path + (query == null ? "" : "?" + query)
                    + (request.isBlank() ? "" : " " + request));
            String body = switch (path) {
                case "/connections/properties" -> """
                        [{"code":"RIUPMI","name":"Riu Demo Palma","currency":"EUR"},
                         {"code":"RIUNEW","name":"Riu Demo Nuevo","currency":"EUR"}]""";
                case "/connections/verify" -> connectionWorks
                        ? "{\"ok\":true,\"message\":\"Token granted\"}"
                        : "{\"ok\":false,\"message\":\"OHIP 401: invalid client\"}";
                // No hotel named: the CRS's catalogue. With one: that Opera property's.
                case "/catalog" -> query == null ? """
                        [{"type":"HOTEL","code":"NEW01","description":"Riu Nuevo"},
                         {"type":"ROOM_TYPE","code":"DBL","description":"Doble"}]"""
                        : "[" + String.join(",", java.util.stream.IntStream.range(0, roomTypes)
                        .mapToObj(n -> "{\"type\":\"ROOM_TYPE\",\"hotelCode\":\"RIUNEW\",\"code\":\"RT" + n + "\"}").toList())
                        + (roomTypes > 0 ? ",{\"type\":\"RATE_PLAN\",\"hotelCode\":\"RIUNEW\",\"code\":\"RACK\"}" : "") + "]";
                case "/pending" -> "[" + String.join(",", java.util.stream.IntStream.range(0, pendingCodes)
                        .mapToObj(n -> "{\"type\":\"ROOM_TYPE\",\"code\":\"C" + n + "\",\"proposed\":false}").toList()) + "]";
                case "/gaps" -> gaps;
                case "/reservations/NEW01/future/usage" -> """
                        {"hotelCode":"NEW01","reservations":5,"codes":[],"partners":[{"partnerCode":"NORDTRAVEL","reservations":2}]}""";
                case "/reservations/NEW01/future" -> future(query);
                case "/partner-profiles/NORDTRAVEL" -> nordtravelIsAProfile || !"GET".equals(exchange.getRequestMethod()) ? "{}" : null;
                // Opera's partners, for an import: one the ERP does not have, one it knows by another name.
                case "/pms-partners" -> """
                        [{"code":"05100908","pmsProfileId":"16120675","profileType":"Agent","name":"ABREU ONLINE PORTUGAL"},
                         {"code":"NORDTRAVEL","pmsProfileId":"16120699","profileType":"Company","name":"Nordtravel AB"},
                         {"code":"12345678","pmsProfileId":"16120701","profileType":"Agent","name":"PLACEHOLDER A"},
                         {"code":"12345678","pmsProfileId":"16120702","profileType":"Company","name":"PLACEHOLDER B"}]""";
                case "/partners/05100908" -> "GET".equals(exchange.getRequestMethod()) ? null : "{}";
                case "/partners/NORDTRAVEL" -> """
                        {"code":"NORDTRAVEL","type":"TravelAgent","name":"Nordtravel","taxId":"SE556677","billingMode":"NoFront"}""";
                default -> "{}";
            };
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
        others.start();
        var url = "http://localhost:" + others.getAddress().getPort();
        registry.add("CRS_INTEGRATION_URL", () -> url);
        registry.add("PMS_INTEGRATION_URL", () -> url);
        registry.add("MAPPING_URL", () -> url);
        registry.add("PARTNERS_URL", () -> url);
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
    }

    @AfterAll
    static void stop() {
        others.stop(0);
    }

    @Autowired
    Integrations lifecycle;
    @Autowired
    io.mateu.ecdemo1.integrations.clients.Services services;
    @Autowired
    Gates gates;
    @Autowired
    Backfill backfill;
    @Autowired
    IntegrationRepository integrations;
    @Autowired
    BackfillRunRepository runs;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        runs.deleteAll();
        integrations.deleteAll();
        calls.clear();
        connectionWorks = true;
        roomTypes = 5;
        pendingCodes = 3;
        gaps = "[]";
        nordtravelIsAProfile = false;
    }

    String register() {
        return lifecycle.register(new Integrations.Registration("NEW01", "RIUNEW", "Riu Nuevo", "https://ohip.example",
                "app", "client", "s3cr3t", "RIUE"), "ana").id;
    }

    @Test
    void anOnboardingGoesThroughItsGatesAndEndsWithTrafficFlowing() throws Exception {
        var id = register();
        var processKey = "alta-integracion:" + id;
        assertThat(consume("upstream", r -> r.value().contains("process-creation-requested") && r.value().contains(processKey), 1, 20))
                .singleElement().satisfies(r -> assertThat(r.value()).contains("\"workflowDefinitionId\":\"alta-integracion\""));

        // Connectivity: verified, and the hotel's own equivalence entered for it.
        lifecycle.stepVerifyConnectivity(id);
        assertThat(calls).anyMatch(c -> c.startsWith("POST /entries/definitions") && c.contains("\"sourceCode\":\"NEW01\"")
                && c.contains("\"targetCode\":\"RIUNEW\""));
        assertGateSignalled(id, "integration-connectivity-ok");

        // The catalogues: the property is configured, codes are pending.
        lifecycle.stepContrastCatalogues(id);
        assertThat(integrations.findById(id)).get().satisfies(i -> {
            assertThat(i.status).isEqualTo(IntegrationStatus.MAPPING_PENDING);
            assertThat(i.contrastSummary).contains("5 room types").contains("3 CRS code(s) without equivalence");
        });
        assertGateSignalled(id, "integration-property-configured");

        // The mapping: the agent is asked, and the gate opens once nothing is pending.
        lifecycle.stepRequestMapping(id);
        assertThat(calls).anyMatch(c -> c.startsWith("POST /agent-proposals?hotelCode=NEW01"));
        assertThat(lifecycle.gateOpen(integrations.findById(id).orElseThrow())).isFalse();
        pendingCodes = 0;
        lifecycle.recheck(id, "test");
        assertGateSignalled(id, "integration-mapping-approved");

        // The partners of the hotel's reservations: announced again until they are PMS profiles.
        lifecycle.stepSyncPartners(id);
        assertThat(calls).anyMatch(c -> c.startsWith("POST /partners/NORDTRAVEL/resync"));
        assertThat(integrations.findById(id).orElseThrow().partnersMissing).containsExactly("NORDTRAVEL");
        nordtravelIsAProfile = true;
        lifecycle.recheck(id, "test");
        assertGateSignalled(id, "integration-partners-synced");

        // The pre-pass: a gap stops it, most blocking first; clearing it opens the gate.
        gaps = """
                [{"kind":"MAPPING","type":"BOARD","code":"MP","reservations":4}]""";
        lifecycle.stepBackfillPrePass(id);
        assertThat(integrations.findById(id).orElseThrow().status).isEqualTo(IntegrationStatus.BACKFILL_BLOCKED);
        assertThat(lifecycle.gateOpen(integrations.findById(id).orElseThrow())).isFalse();
        gaps = "[]";
        lifecycle.recheck(id, "test");
        assertGateSignalled(id, "integration-backfill-clear");

        // The backfill: nearest arrival first, two a tick; the window (10 days) is covered on the
        // third reservation's page; the availability is suspended until it is done.
        lifecycle.stepStartBackfill(id);
        assertThat(integrations.findById(id).orElseThrow().availabilitySuspendedSince).isNotNull();
        backfill.tick();
        assertThat(lifecycle.gateOpen(integrations.findById(id).orElseThrow())).isFalse();
        backfill.tick();
        assertGateSignalled(id, "integration-window-covered");
        backfill.tick();
        assertThat(calls.stream().filter(c -> c.startsWith("POST /projections")).map(c -> c.replaceAll(".*\"locator\":\"(R\\d)\".*", "$1")))
                .containsExactly("R1", "R2", "R3", "R4", "R5");
        assertThat(calls).filteredOn(c -> c.startsWith("POST /projections")).allMatch(c -> c.contains("\"origin\":\"backfill:"));
        assertThat(runs.findAll()).singleElement().satisfies(r -> {
            assertThat(r.status).isEqualTo(BackfillRun.Status.COMPLETED);
            assertThat(r.dispatched).isEqualTo(5);
        });
        assertThat(integrations.findById(id).orElseThrow().availabilitySuspendedSince).isNull();

        // Ready; a person activates; the cause that held the hotel's traffic is resolved.
        lifecycle.stepAwaitActivation(id);
        assertThat(integrations.findById(id).orElseThrow().status).isEqualTo(IntegrationStatus.READY_TO_ACTIVATE);
        assertThat(lifecycle.gateOpen(integrations.findById(id).orElseThrow())).isFalse();
        lifecycle.activate(id, "ana");
        assertGateSignalled(id, "integration-activation-requested");
        lifecycle.stepActivate(id);
        assertThat(integrations.findById(id).orElseThrow().status).isEqualTo(IntegrationStatus.ACTIVE);
        assertThat(calls).anyMatch(c -> c.startsWith("POST /causes/resolve-if-open?key=INTEGRATION_INACTIVE:NEW01"));
        mvc.perform(get("/integrations/hotels/NEW01")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.pmsHotelCode").value("RIUNEW"));

        // Paused, its traffic waits; resumed, what waited goes on.
        lifecycle.pause(id, "ana");
        assertThat(integrations.findById(id).orElseThrow().status).isEqualTo(IntegrationStatus.PAUSED);
        calls.clear();
        lifecycle.resume(id, "ana");
        assertThat(calls).anyMatch(c -> c.startsWith("POST /causes/resolve-if-open?key=INTEGRATION_INACTIVE:NEW01"));
    }

    @Test
    void aConnectionOperaRefusesStopsTheOnboardingUntilItIsFixed() {
        connectionWorks = false;
        var id = register();
        lifecycle.stepVerifyConnectivity(id);
        var failed = integrations.findById(id).orElseThrow();
        assertThat(failed.status).isEqualTo(IntegrationStatus.CONNECTIVITY_FAILED);
        assertThat(failed.connectivityMessage).contains("invalid client");
        assertThat(lifecycle.gateOpen(failed)).isFalse();
        assertThat(consume("notifications", r -> r.value().contains("INTEGRATION_NEEDS_ATTENTION") && r.value().contains("NEW01"), 1, 15))
                .hasSize(1);

        connectionWorks = true;
        lifecycle.changeConnection(id, new Integrations.ConnectionChange(null, null, null, "n3w", null), "ana");
        var fixed = integrations.findById(id).orElseThrow();
        assertThat(fixed.status).isEqualTo(IntegrationStatus.CREATED);
        assertThat(lifecycle.gateOpen(fixed)).isTrue();
        assertThat(lifecycle.connection(fixed).clientSecret()).isEqualTo("n3w");
    }

    @Test
    void aPropertyNobodyConfiguredWaitsForItsConfiguration() {
        roomTypes = 0;
        var id = register();
        lifecycle.stepVerifyConnectivity(id);
        lifecycle.stepContrastCatalogues(id);
        assertThat(integrations.findById(id).orElseThrow().status).isEqualTo(IntegrationStatus.PENDING_CONFIGURATION);
        assertThat(lifecycle.gateOpen(integrations.findById(id).orElseThrow())).isFalse();

        roomTypes = 4;
        lifecycle.recheck(id, "test");
        var configured = integrations.findById(id).orElseThrow();
        assertThat(configured.status).isEqualTo(IntegrationStatus.MAPPING_PENDING);
        assertThat(lifecycle.gateOpen(configured)).isTrue();
    }

    @Test
    void theSecretIsSealedAtRestAndOnlyTheConnectorsEndpointHandsItOut() throws Exception {
        var id = register();
        var stored = jdbc.queryForObject("select client_secret_sealed from integration where id = ?", String.class, id);
        assertThat(stored).isNotBlank().doesNotContain("s3cr3t");

        mvc.perform(get("/integrations/{id}", id)).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("s3cr3t"));
        mvc.perform(get("/integrations/connections/RIUNEW")).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("s3cr3t"))
                .andExpect(jsonPath("$.gatewayUrl").value("https://ohip.example"));
        mvc.perform(get("/integrations/hotels/NOPE01")).andExpect(status().isNotFound());
    }

    @Test
    void anIntegrationStartsFromTheChainsConnectionAndOperaListsItsProperties() {
        assertThat(lifecycle.operaProperties()).extracting(p -> p.code()).containsExactly("RIUPMI", "RIUNEW");
        assertThat(calls).anyMatch(c -> c.startsWith("POST /connections/properties") && c.contains("\"clientSecret\":\"ch41n\""));

        // The form leaves the connection as it came: only the hotel and the property are named.
        var id = lifecycle.register(new Integrations.Registration("NEW01", "RIUNEW", "Riu Nuevo", null, null, null,
                null, null), "ana").id;
        assertThat(lifecycle.connection(lifecycle.find(id)))
                .extracting(c -> c.gatewayUrl(), c -> c.clientId(), c -> c.clientSecret(), c -> c.enterpriseId())
                .containsExactly("https://ohip.example", "chain-client", "ch41n", "RIUE");
    }

    @Test
    void theCrsHotelsComeFromTheCrs() {
        assertThat(services.crsHotels()).extracting(h -> h.code()).containsExactly("NEW01");
    }

    @Test
    void aHotelHasOneIntegration() {
        register();
        assertThatThrownBy(this::register).isInstanceOf(IllegalStateException.class).hasMessageContaining("already has");
    }

    @Test
    void partnersAreImportedFromOperaIntoTheErpAndNothingIsWrittenToOpera() {
        var id = register();
        calls.clear();

        var i = lifecycle.importPartners(id, "ana");

        // The type equivalences come with the import: certain, since the partners came from Opera.
        assertThat(calls).anyMatch(c -> c.startsWith("POST /entries/definitions") && c.contains("\"PARTNER_TYPE\"")
                && c.contains("\"sourceCode\":\"TravelAgent\"") && c.contains("\"targetCode\":\"Agent\""));
        // New to the ERP: created with Opera's name and type; who pays is the guest until the ERP says otherwise.
        assertThat(calls).anyMatch(c -> c.startsWith("POST /partners ") && c.contains("\"code\":\"05100908\"")
                && c.contains("\"name\":\"ABREU ONLINE PORTUGAL\"") && c.contains("\"type\":\"TravelAgent\"")
                && c.contains("\"billingMode\":\"Front\""));
        // Known to the ERP: Opera's name and type, and what only the ERP knew kept.
        assertThat(calls).anyMatch(c -> c.startsWith("PUT /partners/NORDTRAVEL") && c.contains("\"type\":\"Company\"")
                && c.contains("\"name\":\"Nordtravel AB\"") && c.contains("\"billingMode\":\"NoFront\"")
                && c.contains("\"taxId\":\"SE556677\""));
        // The mapping learns which profile each one already is.
        assertThat(calls).anyMatch(c -> c.startsWith("PUT /partner-profiles/05100908") && c.contains("\"pmsProfileId\":\"16120675\"")
                && c.contains("\"profileType\":\"Agent\""));
        assertThat(calls).anyMatch(c -> c.startsWith("PUT /partner-profiles/NORDTRAVEL") && c.contains("\"pmsProfileId\":\"16120699\""));
        // Nothing asked of the connector but the list: no partner is projected to Opera.
        assertThat(calls).noneMatch(c -> c.contains("/resync") || c.contains("/crm/"));
        // A code on two profiles is nobody in particular: left out, and said.
        assertThat(calls).noneMatch(c -> c.contains("12345678"));
        assertThat(i.history).anyMatch(h -> h.what().contains("1 new, 1 updated, 0 unchanged")
                && h.what().contains("left out, on more than one Opera profile: 12345678"));
    }

    void assertGateSignalled(String id, String gate) {
        var integration = integrations.findById(id).orElseThrow();
        assertThat(integration.gate).isEqualTo(gate);
        assertThat(lifecycle.gateOpen(integration)).isTrue();
        gates.look();
        assertThat(consume("upstream", r -> r.value().contains("\"messageName\":\"" + gate + "\"")
                && r.value().contains(integration.processKey), 1, 15)).isNotEmpty();
    }

    JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
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
