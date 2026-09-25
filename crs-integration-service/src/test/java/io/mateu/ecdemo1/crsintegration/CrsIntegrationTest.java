package io.mateu.ecdemo1.crsintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CRS adapter against a real Postgres and broker, with the CRS itself (booking, partners)
 * played by a small HTTP server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CrsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    // Data on a tmpfs: Redpanda refuses writes once the disk it sits on has less free space than its
    // threshold, and a developer machine with a nearly full disk then fails every test after the
    // first with "BrokerNotAvailable". In memory it is also faster, and a test broker holds nothing
    // worth keeping.
    static RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7")
            .withTmpFs(java.util.Map.of("/var/lib/redpanda/data", "rw"));

    static HttpServer crs;
    static final List<String> writes = new CopyOnWriteArrayList<>();

    static final String BOOKING = """
            {"id":"LOC1","hotelCode":"PMI01","currency":"EUR","status":"Confirmed","version":3,"channelCode":"TTOO",
             "partnerCode":"NORDTRAVEL","externalReference":"V-1","arrival":"2026-10-05","departure":"2026-10-07",
             "nights":2,"somethingNew":"a field the adapter does not know",
             "holder":{"firstName":"Ana","lastName":"García","email":"ana@example.com"},
             "rooms":[{"line":1,"roomTypeCode":"DBL","ratePlanCode":"TTOO","boardCode":"AD","adults":2,"childrenAges":[7],
                       "guests":[{"firstName":"Leo","lastName":"García","type":"Child","age":7}],
                       "nightlyRates":[{"date":"2026-10-05","amount":130.50},{"date":"2026-10-06","amount":130.50}],
                       "total":261.00}],
             "payments":[{"paymentId":"P1","type":"Deposit","methodCode":"VISA","amount":50,"date":"2026-09-22"}],
             "totalAmount":261.00}""";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        crs = HttpServer.create(new InetSocketAddress(0), 0);
        crs.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            if (!"GET".equals(exchange.getRequestMethod())) {
                writes.add(exchange.getRequestMethod() + " " + path + " "
                        + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            String body = switch (path) {
                case "/bookings/LOC1" -> BOOKING;
                case "/bookings/LOC3" -> BOOKING.replace("\"LOC1\"", "\"LOC3\"").replace("\"PMI01\"", "\"CUN01\"");
                // PMI01 has an integration; CUN01 has none.
                case "/integrations/views" -> """
                        [{"id":"I-1","crsHotelCode":"PMI01","pmsHotelCode":"XMAR","status":"ACTIVE"}]""";
                case "/bookings/future" -> exchange.getRequestURI().getQuery().contains("afterArrival")
                        ? "[]"
                        : "[" + BOOKING + "," + BOOKING.replace("\"LOC1\"", "\"LOC2\"").replace("2026-10-05", "2026-10-09")
                                .replace("\"TTOO\"", "\"WEB\"").replace("\"NORDTRAVEL\"", "null") + "]";
                case "/partners/NORDTRAVEL" -> """
                        {"code":"NORDTRAVEL","type":"TourOperator","name":"Nordic Travel Group AB","billingMode":"NoFront",
                         "active":true,"version":1}""";
                default -> null;
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
        crs.start();
        var url = "http://localhost:" + crs.getAddress().getPort();
        registry.add("BOOKING_URL", () -> url);
        registry.add("PARTNERS_URL", () -> url);
        registry.add("INTEGRATIONS_URL", () -> url);
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
    }

    @AfterAll
    static void stop() {
        crs.stop(0);
    }

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockMvc mvc;

    @Test
    void aBookingEventBecomesABusinessEventAndStartsItsProcessOnceEvenIfRepeated() throws Exception {
        var event = """
                {"type":"booking-modified","eventId":"E-1","bookingId":"LOC1","hotelCode":"PMI01","version":3,
                 "occurredAt":"2026-09-22T10:00:00Z","change":"Confirmed"}""";
        send("crs-bookings", "LOC1", event);
        send("crs-bookings", "LOC1", event);

        var business = consume("integration-events", r -> r.value().contains("E-1"), 2, 15);
        assertThat(business).singleElement().satisfies(r -> {
            assertThat(r.key()).isEqualTo("PMI01/LOC1");
            assertThat(json(r.value()).get("type").asText()).isEqualTo("reservation-modified");
        });

        var starts = consume("upstream", r -> r.value().contains("E-1"), 2, 15);
        assertThat(starts).singleElement().satisfies(r -> {
            var start = json(r.value());
            assertThat(start.get("type").asText()).isEqualTo("process-creation-requested");
            assertThat(start.get("workflowDefinitionId").asText()).isEqualTo("proyectar-reserva");
            assertThat(start.get("businessKey").asText()).isEqualTo("proyectar-reserva:PMI01/LOC1:E-1");
            assertThat(variables(start)).containsEntry("locator", "LOC1").containsEntry("hotelCode", "PMI01")
                    .containsEntry("version", "3").containsEntry("processKey", "proyectar-reserva:PMI01/LOC1:E-1");
        });
    }

    @Test
    void aCancellationStartsTheCancellationProcessAndAPartnerChangeThePartnerOne() throws Exception {
        send("crs-bookings", "LOC1", """
                {"type":"booking-cancelled","eventId":"E-2","bookingId":"LOC1","hotelCode":"PMI01","version":4,
                 "occurredAt":"2026-09-22T10:00:00Z","reasonCode":"CLI"}""");
        send("partners", "NORDTRAVEL", """
                {"type":"partner-changed","eventId":"E-3","partnerCode":"NORDTRAVEL","version":1,
                 "occurredAt":"2026-09-22T10:00:00Z"}""");

        assertThat(consume("upstream", r -> r.value().contains("E-2"), 1, 15))
                .singleElement().satisfies(r -> assertThat(json(r.value()).get("workflowDefinitionId").asText())
                        .isEqualTo("proyectar-cancelacion"));
        assertThat(consume("upstream", r -> r.value().contains("E-3"), 1, 15))
                .singleElement().satisfies(r -> assertThat(variables(json(r.value())))
                        .containsEntry("partnerCode", "NORDTRAVEL"));
    }

    @Test
    void aReservationOfAHotelWithNoIntegrationStartsNoProcess() throws Exception {
        send("crs-bookings", "LOC3", """
                {"type":"booking-created","eventId":"E-5","bookingId":"LOC3","hotelCode":"CUN01","version":1,
                 "occurredAt":"2026-09-22T10:00:00Z"}""");

        assertThat(consume("integration-events", r -> r.value().contains("E-5"), 1, 15)).singleElement();
        assertThat(consume("upstream", r -> r.value().contains("E-5"), 1, 6)).isEmpty();
    }

    @Test
    void aCustomerTheMdmChangedHasItsReservationsProjectedAgainWhereThereIsAnIntegration() throws Exception {
        send("customers", "C-1", """
                {"type":"customer-changed","eventId":"M-1","occurredAt":"2026-09-25T10:00:00Z","customerId":"C-1","version":2,
                 "data":{"firstName":"Ana","lastName":"García","email":"ana.maria@example.com"},"dataChanged":true,
                 "changeRequestId":"CR-1","decision":"APPROVED","reservations":["PMI01/LOC1","CUN01/LOC3"]}""");
        send("customers", "C-2", """
                {"type":"customer-changed","eventId":"M-2","occurredAt":"2026-09-25T10:00:00Z","customerId":"C-2","version":5,
                 "data":{"firstName":"Leo"},"dataChanged":false,"changeRequestId":"CR-2","decision":"REJECTED",
                 "reservations":["PMI01/LOC1"]}""");

        // PMI01 has an integration: projected again, with an origin the connector writes the profile for.
        assertThat(consume("upstream", r -> r.value().contains("mdm-update-C-1-v2"), 2, 15)).singleElement()
                .satisfies(r -> assertThat(json(r.value()).get("businessKey").asText())
                        .isEqualTo("proyectar-reserva:PMI01/LOC1:mdm-update-C-1-v2"));
        // CUN01 has none; and a rejection changed nothing, so nothing is written again.
        assertThat(consume("upstream", r -> r.value().contains("CUN01/LOC3") || r.value().contains("C-2"), 1, 5)).isEmpty();
    }

    @Test
    void aBookingThatNoLongerExistsIsNotIntegrated() throws Exception {
        send("crs-bookings", "GONE", """
                {"type":"booking-created","eventId":"E-4","bookingId":"GONE","hotelCode":"PMI01","version":1,
                 "occurredAt":"2026-09-22T10:00:00Z"}""");

        assertThat(consume("integration-events", r -> r.value().contains("E-4"), 1, 6)).isEmpty();
    }

    @Test
    void theAnnotationStepWritesThePmsReferenceBackAndAnswersTheEngine() throws Exception {
        var task = new TaskExecutionRequested("TE-1", "PROC-1", "proyectar-reserva", "annotate-pms-reference", "",
                List.of(new Variable("locator", "LOC1"), new Variable("pmsReservationId", "OPERA-77")));
        send("crs-integration", "PROC-1", objectMapper.writerFor(DomainEvent.class).writeValueAsString(task));

        var replies = consume("upstream", r -> r.value().contains("TE-1"), 1, 15);
        assertThat(replies).singleElement().satisfies(r -> assertThat(json(r.value()).get("status").asText())
                .isEqualTo("COMPLETED"));
        assertThat(writes).anySatisfy(w -> assertThat(w).startsWith("PUT /bookings/LOC1/pms-reference")
                .contains("OPERA-77"));
    }

    @Test
    void aBackfillPagesThroughTheFutureReservationsAndSeesWhatTheyReallyUse() throws Exception {
        mvc.perform(get("/reservations/PMI01/future?limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].locator").value("LOC1"))
                .andExpect(jsonPath("$[1].locator").value("LOC2"))
                .andExpect(jsonPath("$[1].version").value(3));

        var usage = json(mvc.perform(get("/reservations/PMI01/future/usage")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(usage.get("reservations").asInt()).isEqualTo(2);
        var codes = new java.util.HashMap<String, Integer>();
        usage.get("codes").forEach(c -> codes.put(c.get("type").asText() + "/" + c.get("code").asText(), c.get("reservations").asInt()));
        assertThat(codes).containsEntry("ROOM_TYPE/DBL", 2).containsEntry("CHANNEL/TTOO", 1).containsEntry("CHANNEL/WEB", 1)
                .containsEntry("PAYMENT_METHOD/VISA", 2);
        assertThat(usage.get("codes").get(0).get("reservations").asInt()).isEqualTo(2);
        assertThat(usage.get("partners")).singleElement()
                .satisfies(p -> assertThat(p.get("partnerCode").asText()).isEqualTo("NORDTRAVEL"));
    }

    @Test
    void aBackfillProjectsAReservationByTheSamePathOnceEvenIfAskedTwice() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/projections").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"hotelCode":"PMI01","locator":"LOC1","origin":"backfill:R1"}"""))
                    .andExpect(status().isOk());
        }
        var starts = consume("upstream", r -> r.value().contains("backfill:R1"), 2, 15);
        assertThat(starts).singleElement().satisfies(r -> {
            var start = json(r.value());
            assertThat(start.get("workflowDefinitionId").asText()).isEqualTo("proyectar-reserva");
            assertThat(start.get("businessKey").asText()).isEqualTo("proyectar-reserva:PMI01/LOC1:backfill:R1");
            assertThat(variables(start)).containsEntry("origin", "backfill:R1").containsEntry("locator", "LOC1")
                    .doesNotContainKey("version");
        });
    }

    @Test
    void theReservationIsReadInTheIntegrationsTerms() throws Exception {
        mvc.perform(get("/reservations/PMI01/LOC1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.rooms[0].guests[0].type").value("CHILD"))
                .andExpect(jsonPath("$.payments[0].type").value("DEPOSIT"));
        mvc.perform(get("/reservations/CUN01/LOC1")).andExpect(status().isNotFound());
        mvc.perform(get("/partners/NORDTRAVEL"))
                .andExpect(jsonPath("$.type").value("TOUR_OPERATOR"))
                .andExpect(jsonPath("$.billingMode").value("NO_FRONT"));
    }

    static Map<String, String> variables(JsonNode start) {
        var map = new java.util.HashMap<String, String>();
        start.get("variables").forEach(v -> map.put(v.get("name").asText(), v.get("value").asText()));
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
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers(),
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads the topic from the start, for the given seconds or until {@code enough} matches arrived. */
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
