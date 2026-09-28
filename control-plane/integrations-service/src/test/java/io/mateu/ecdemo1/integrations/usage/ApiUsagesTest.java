package io.mateu.ecdemo1.integrations.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import io.mateu.ecdemo1.integrations.config.TolerantReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The header's figures: each service's own count, fetched and added up, reused a few seconds, and
 * nothing — rather than a wrong number — when no service answers.
 */
class ApiUsagesTest {

    static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");
    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    HttpServer services;
    final AtomicInteger asked = new AtomicInteger();
    String base;

    static ApiUsage salesforce(long used, long ours, boolean paused) {
        return new ApiUsage("salesforce", "customer-mdm", used, 15000L, NOW, ours, Map.of("poll", ours), null,
                used - ours, 0, 0, paused, null, null, null, 0, 0, null);
    }

    static ApiUsage opera(String service, long ours, Map<String, Long> byPurpose, long limited) {
        return new ApiUsage("opera", service, null, null, null, ours, byPurpose, Map.of("GET /rsv", ours), null,
                limited, 0, false, null, null, null, 0, 0, null);
    }

    @BeforeEach
    void start() throws Exception {
        services = HttpServer.create(new InetSocketAddress(0), 0);
        services.createContext("/a/usage/salesforce", exchange -> reply(exchange, salesforce(14908, 120, false)));
        services.createContext("/a/usage/opera", exchange -> reply(exchange, opera("pms-integration", 300,
                Map.of("reservations", 200L, "profiles", 100L), 0)));
        services.createContext("/b/usage/opera", exchange -> reply(exchange, opera("front-office", 20,
                Map.of("reservations", 20L), 2)));
        services.start();
        base = "http://localhost:" + services.getAddress().getPort();
    }

    void reply(com.sun.net.httpserver.HttpExchange exchange, ApiUsage usage) throws java.io.IOException {
        asked.incrementAndGet();
        var bytes = JSON.writeValueAsString(usage).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterEach
    void stop() {
        services.stop(0);
    }

    ApiUsages usages(List<String> salesforce, List<String> opera) {
        return new ApiUsages(new UsageProperties(salesforce, opera, Duration.ofSeconds(1), Duration.ofSeconds(15)),
                new TolerantReader(JSON), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void oneServicesCountIsTheUsageAsItSaidIt() {
        var u = usages(List.of(base + "/a"), List.of()).salesforce().orElseThrow();
        assertThat(u.orgUsed()).isEqualTo(14908);
        assertThat(u.ours24h()).isEqualTo(120);
        assertThat(u.others24h()).isEqualTo(14788);
    }

    @Test
    void severalServicesCallingOperaAreAddedUp() {
        var u = usages(List.of(), List.of(base + "/a", base + "/b")).opera().orElseThrow();
        assertThat(u.ours24h()).isEqualTo(320);
        assertThat(u.byPurpose()).containsExactly(Map.entry("reservations", 220L), Map.entry("profiles", 100L));
        assertThat(u.limited24h()).isEqualTo(2);
        assertThat(u.service()).isEqualTo("pms-integration+front-office");
    }

    @Test
    void aServiceThatDoesNotAnswerIsLeftOutAndNoneAnsweringIsNoUsage() {
        var usages = usages(List.of("http://localhost:1"), List.of(base + "/a", "http://localhost:1"));
        assertThat(usages.salesforce()).isEmpty();
        assertThat(usages.opera()).get().extracting(ApiUsage::ours24h).isEqualTo(300L);
    }

    @Test
    void manyConsolesAskingAreOneQuestionToTheServices() {
        var usages = usages(List.of(base + "/a"), List.of());
        usages.salesforce();
        usages.salesforce();
        usages.salesforce();
        assertThat(asked).hasValue(1);
    }

    @Test
    void figuresFitInAHeader() {
        assertThat(ApiUsages.compact(950)).isEqualTo("950");
        assertThat(ApiUsages.compact(14908)).isEqualTo("14.9k");
        assertThat(ApiUsages.compact(15000)).isEqualTo("15k");
        assertThat(ApiUsages.compact(1_234_567)).isEqualTo("1235k");
    }

    @Test
    void theColourSaysHowCloseTheOrgIsToItsAllowance() {
        assertThat(UsageLevel.of(salesforce(10000, 10, false))).isEqualTo(UsageLevel.OK);
        assertThat(UsageLevel.of(salesforce(12000, 10, false))).isEqualTo(UsageLevel.WARNING);
        assertThat(UsageLevel.of(salesforce(14250, 10, false))).isEqualTo(UsageLevel.ERROR);
        assertThat(UsageLevel.of(salesforce(1000, 10, true))).isEqualTo(UsageLevel.ERROR);
        assertThat(UsageLevel.of(null)).isEqualTo(UsageLevel.UNKNOWN);
        // Salesforce not heard from yet: unknown, not fine.
        assertThat(UsageLevel.of(new ApiUsage("salesforce", "customer-mdm", null, null, null, 0, null, null, null, 0, 0,
                false, null, null, null, 0, 0, null))).isEqualTo(UsageLevel.UNKNOWN);
        assertThat(UsageLevel.of(opera("pms-integration", 300, Map.of(), 0))).isEqualTo(UsageLevel.OK);
        assertThat(UsageLevel.of(opera("pms-integration", 300, Map.of(), 1))).isEqualTo(UsageLevel.WARNING);
    }
}
