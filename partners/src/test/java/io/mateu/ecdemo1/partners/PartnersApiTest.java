package io.mateu.ecdemo1.partners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PartnersApiTest {

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

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void changesAndResyncsAreAnnouncedWithTheVersionTheyLeaveThePartnerAt() throws Exception {
        var seeded = json(mvc.perform(get("/partners/BOOKIT")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(seeded.get("version").asLong()).isEqualTo(1);

        var details = """
                {"type":"OnlineAgency","name":"Bookit Online Group S.L.","taxId":"B12345678",
                 "address":{"line":"Calle Mayor 1","city":"Madrid","postalCode":"28013","countryCode":"ES"},
                 "email":"partners@bookit.example","billingMode":"Front"}""";
        mvc.perform(put("/partners/BOOKIT").contentType(MediaType.APPLICATION_JSON).content(details))
                .andExpect(status().isOk());
        mvc.perform(post("/partners/BOOKIT/resync")).andExpect(status().isOk());

        var after = json(mvc.perform(get("/partners/BOOKIT")).andReturn().getResponse().getContentAsString());
        assertThat(after.get("name").asText()).isEqualTo("Bookit Online Group S.L.");
        assertThat(after.get("version").asLong()).isEqualTo(2);

        assertThat(consume("BOOKIT", 3)).map(r -> json(r.value()))
                .extracting(e -> e.get("type").asText() + "@" + e.get("version").asLong())
                .containsExactly("partner-changed@1", "partner-changed@2", "partner-changed@2");
    }

    @Test
    void aPartnerCodeIsValidated() throws Exception {
        mvc.perform(post("/partners").contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"bad code","details":{"type":"Company","name":"X","billingMode":"Front"}}"""))
                .andExpect(status().isBadRequest());
    }

    JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static List<ConsumerRecord<String, String>> consume(String key, int expected) {
        var found = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("partners"));
            var deadline = Instant.now().plusSeconds(20);
            while (found.size() < expected && Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                }
            }
        }
        return found;
    }
}
