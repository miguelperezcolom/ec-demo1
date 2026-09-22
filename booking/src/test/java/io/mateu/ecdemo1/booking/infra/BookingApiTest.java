package io.mateu.ecdemo1.booking.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.Fixtures;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The booking service against a real Postgres and a real Kafka-compatible broker: what the REST
 * API does to a booking, and what reaches the topic because of it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookingApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7");

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    BookingRepository repository;
    @Autowired
    TransactionTemplate transactions;

    static final String REQUEST = """
            {"channelCode":"WEB","arrival":"2026-10-05","departure":"2026-10-08",
             "holder":{"firstName":"Ana","lastName":"García","email":"ana@example.com","nationality":"ES"},
             "rooms":[{"roomTypeCode":"DBL","ratePlanCode":"BAR","boardCode":"AD","adults":2,"childrenAges":[],
                       "guests":[{"firstName":"Ana","lastName":"García","type":"Adult"}]}]}
            """;

    @Test
    void everyChangeReachesTheTopicInOrderWithItsVersionAndTheAnnotationDoesNot() throws Exception {
        var id = create(REQUEST);
        mvc.perform(put("/bookings/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("2026-10-08", "2026-10-09")))
                .andExpect(status().isOk());
        mvc.perform(post("/bookings/{id}/payments", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"Deposit","methodCode":"VISA","amount":100}"""))
                .andExpect(status().isCreated());
        mvc.perform(put("/bookings/{id}/pms-reference", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":"OPERA-1"}"""))
                .andExpect(status().isOk());
        mvc.perform(post("/bookings/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasonCode":"CLI"}"""))
                .andExpect(status().isOk());

        var booking = read(id);
        assertThat(booking.get("status").asText()).isEqualTo("Cancelled");
        assertThat(booking.get("version").asLong()).isEqualTo(4);
        assertThat(booking.get("nights").asInt()).isEqualTo(4);
        assertThat(booking.get("pmsReference").get("reservationId").asText()).isEqualTo("OPERA-1");
        assertThat(booking.get("rooms").get(0).get("nightlyRates")).hasSize(4);
        assertThat(booking.get("rooms").get(0).get("guests").get(0).get("firstName").asText()).isEqualTo("Ana");

        var events = consume("crs-bookings", id, 4);
        assertThat(events).map(r -> r.key()).containsOnly(id);
        assertThat(events).map(r -> json(r.value()))
                .extracting(e -> e.get("type").asText() + "@" + e.get("version").asLong())
                .containsExactly("booking-created@1", "booking-modified@2", "booking-modified@3",
                        "booking-cancelled@4");
    }

    @Test
    void creatingABookingRequestsItsPaymentVerificationThroughTheOutbox() throws Exception {
        var id = create(REQUEST);

        var requests = consume("upstream", null, 50).stream()
                .map(r -> json(r.value()))
                .filter(e -> e.toString().contains("verify-payment-for-" + id))
                .toList();
        assertThat(requests).singleElement()
                .satisfies(e -> assertThat(e.get("type").asText()).isEqualTo("process-creation-requested"));
    }

    @Test
    void nestedPartsAreStoredAsReadableJson() throws Exception {
        var id = create(REQUEST);

        var rooms = jdbc.queryForObject("select rooms::text from crs_booking where id = ?", String.class, id);
        // jsonb normalises the text it gives back: a space after every colon.
        assertThat(rooms).contains("\"date\": \"2026-10-05\"").contains("\"roomTypeCode\": \"DBL\"");
    }

    @Test
    void anUnknownCodeIsRefusedWithTheValidOnes() throws Exception {
        var body = mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(command(REQUEST.replace("\"DBL\"", "\"SUI\""))))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(json(body).get("detail").asText()).contains("Unknown room type").contains("DBLSV");
    }

    @Test
    void aTourOperatorSaleNeedsAPartner() throws Exception {
        mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(command(REQUEST.replace("\"WEB\"", "\"TTOO\""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCancelledBookingCannotBeConfirmed() throws Exception {
        var id = create(REQUEST);
        mvc.perform(post("/bookings/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reasonCode":"CLI"}"""));

        mvc.perform(post("/bookings/{id}/confirm", id)).andExpect(status().isConflict());
    }

    @Test
    void aChangeMadeOnAStaleCopyIsRefusedInsteadOfReusingAVersion() throws Exception {
        var id = create(REQUEST);
        var stale = repository.findById(new BookingId(id)).orElseThrow();

        transactions.executeWithoutResult(tx -> {
            var fresh = repository.findByIdForUpdate(new BookingId(id)).orElseThrow();
            fresh.confirm(Instant.now());
            repository.save(fresh);
        });

        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            stale.update(Fixtures.terms(2), Instant.now());
            repository.save(stale);
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("changed concurrently");
    }

    String create(String request) throws Exception {
        var body = mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(command(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(body).get("id").asText();
    }

    static String command(String request) {
        return "{\"hotelCode\":\"PMI01\",\"booking\":" + request + "}";
    }

    JsonNode read(String id) throws Exception {
        return json(mvc.perform(get("/bookings/{id}", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads the topic from the beginning until {@code expected} records with the given key have
     * arrived (any key when null), or twenty seconds pass.
     */
    static List<ConsumerRecord<String, String>> consume(String topic, String key, int expected) {
        var found = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            var deadline = Instant.now().plusSeconds(20);
            while (found.size() < expected && Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (key == null || key.equals(record.key())) {
                        found.add(record);
                    }
                }
                if (key == null && !found.isEmpty() && Instant.now().isAfter(deadline.minusSeconds(15))) {
                    break;
                }
            }
        }
        return found;
    }
}
