package io.mateu.ecdemo1.mapping.proposals;

import io.mateu.ecdemo1.mapping.config.MappingProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;

/**
 * Asks the agent for a mapping proposal. It only starts the conversation: the agent reads the
 * pending codes and the PMS catalog through this service's MCP tools and registers its proposal
 * with them, and nothing it proposes is in force until a person approves it.
 *
 * <p>The request goes as the person who pressed the button — their token is passed on — and from
 * the dictionary screen's route, which is what the control plane's routing rule keys on to
 * pick the mapping agent rather than the console's general one.
 */
@Component
public class AgentProposals {

    static final String ROUTE = "/mapping/dictionary";

    final RestClient agent;

    public AgentProposals(MappingProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        // A proposal is a conversation with tool calls in it: minutes, not milliseconds.
        factory.setReadTimeout(Duration.ofMinutes(4));
        this.agent = RestClient.builder().baseUrl(properties.iaAgentUrl()).requestFactory(factory).build();
    }

    /**
     * Asks without waiting for the answer — for the onboarding, which cannot hold a step for the
     * minutes a proposal takes. It goes as no one in particular: the control plane routes it by the
     * screen, and the proposal is still only a proposal.
     */
    public void requestProposalInBackground(String hotelCode) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                var answer = requestProposal(hotelCode, null);
                org.slf4j.LoggerFactory.getLogger(AgentProposals.class)
                        .info("Agent's proposal for {}: {}", hotelCode, answer == null ? "" : answer.lines().findFirst().orElse(""));
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(AgentProposals.class)
                        .warn("The agent did not propose a mapping for {}: {}", hotelCode, e.getMessage());
            }
        });
    }

    public String requestProposal(String hotelCode, String authorization) {
        var body = new HashMap<String, Object>();
        body.put("message", """
                Propón el mapeado pendiente del hotel %s del CRS al PMS. Lee los códigos pendientes con
                listPendingCodes y el catálogo del PMS con getPmsCatalog; si el propio hotel está pendiente,
                propón primero su equivalencia. Registra todas las propuestas con proposeMappings, con tu
                confianza y el porqué de cada una, y termina con un resumen breve de lo que propones y de lo
                que no has sabido emparejar. No apruebes nada.""".formatted(hotelCode));
        body.put("sessionId", "mapping-proposal-" + hotelCode + "-" + UUID.randomUUID());
        body.put("currentRoute", ROUTE);
        body.put("locale", "es");
        var request = agent.post().uri("/ai/api/agent/chat").contentType(MediaType.APPLICATION_JSON).body(body);
        if (authorization != null && !authorization.isBlank()) {
            request = request.header("Authorization", authorization);
        }
        return request.retrieve().body(String.class);
    }
}
