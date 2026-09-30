package io.mateu.ecdemo1.booking.infra.out.audit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** Who, as the CRS names them in its audit trail: the person, the console's agent for them, or nobody known. */
class OutboxAuditTrailTest {

    static String token(String username) {
        var enc = Base64.getUrlEncoder().withoutPadding();
        return "Bearer " + enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(("{\"preferred_username\":\"" + username + "\"}").getBytes(StandardCharsets.UTF_8)) + ".x";
    }

    @Test
    void thePersonOfTheToken() {
        var request = new MockHttpServletRequest("POST", "/bookings/ABC/cancel");
        request.addHeader("Authorization", token("ana"));
        assertThat(OutboxAuditTrail.actorOf(request)).isEqualTo("ana");
    }

    @Test
    void theNameACallerSetWins() {
        var request = new MockHttpServletRequest("POST", "/walk-ins");
        request.addHeader("X-User-Name", "luis");
        request.addHeader("Authorization", token("ana"));
        assertThat(OutboxAuditTrail.actorOf(request)).isEqualTo("luis");
    }

    @Test
    void theConsolesAgentForThePersonOnTheMcpTools() {
        var request = new MockHttpServletRequest("POST", "/mcp/message");
        request.addHeader("Authorization", token("ana"));
        assertThat(OutboxAuditTrail.actorOf(request)).isEqualTo("console-agent (ana)");
    }

    @Test
    void theActionItWritesIsWhatTheAuditTopicSays() {
        var written = new java.util.ArrayList<String>();
        var outbox = new io.mateu.ecdemo1.messaging.Outbox(null, null, null, null) {
            @Override
            public void append(String destination, String key, String type, String payload,
                               java.util.Map<String, String> headers) {
                assertThat(destination).isEqualTo("audit");
                written.add(payload);
            }
        };
        // the application's mapper, as Spring Boot builds it
        var mapper = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        var trail = new OutboxAuditTrail(outbox, (com.fasterxml.jackson.databind.ObjectMapper) mapper,
                java.time.Clock.systemUTC());

        trail.record("Booking cancelled", "ABC123", "MRU01", "ana",
                new java.util.LinkedHashMap<>(java.util.Map.of("locator", "ABC123", "reason", "CLI")), true, "Cancelada");

        assertThat(written).singleElement().satisfies(json -> {
            io.mateu.ecdemo1.contracts.testing.Contracts.topic("audit").assertValid(json);
            assertThat(json).contains("\"service\":\"booking\"", "ABC123");
        });
    }

    @Test
    void aCallWithNobodyIsTheApi() {
        assertThat(OutboxAuditTrail.actorOf(new MockHttpServletRequest("GET", "/bookings"))).isEqualTo("api");
    }
}
