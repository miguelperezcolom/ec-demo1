package io.mateu.ecdemo1.mapping;

import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.proposals.AgentProposals;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Ask the agent" without an LLM: what reaches the agent service. The agent is picked by the
 * control plane's routing rule on the screen's route, and acts as the person who pressed the
 * button — so both must travel.
 */
class AgentPathTest {

    @Test
    void theRequestCarriesTheScreensRouteAndThePersonsToken() throws Exception {
        var received = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var agent = HttpServer.create(new InetSocketAddress(0), 0);
        agent.createContext("/ai/api/agent/chat", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            var answer = "Propuestas registradas: 6".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        agent.start();
        try {
            var proposals = new AgentProposals(new MappingProperties(null, null, null,
                    "http://localhost:" + agent.getAddress().getPort(), null, Duration.ofSeconds(30)));

            var answer = proposals.requestProposal("PMI01", "Bearer the-persons-token");

            assertThat(answer).isEqualTo("Propuestas registradas: 6");
            assertThat(authorization.get()).isEqualTo("Bearer the-persons-token");
            assertThat(received.get()).contains("\"currentRoute\":\"/mapping/pending\"").contains("PMI01")
                    .contains("listPendingCodes").contains("proposeMappings").contains("No apruebes nada");
        } finally {
            agent.stop(0);
        }
    }

    @Test
    void codeTypesTheAgentIsToldAboutExist() {
        // The system context names these; a rename would leave the agent proposing a type that fails.
        assertThat(CodeType.valueOf("CHANNEL")).isNotNull();
        assertThat(CodeType.valueOf("PARTNER_TYPE")).isNotNull();
        assertThat(CodeType.valueOf("MARKET")).isNotNull();
    }
}
