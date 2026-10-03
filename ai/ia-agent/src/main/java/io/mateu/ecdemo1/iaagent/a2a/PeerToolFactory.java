package io.mateu.ecdemo1.iaagent.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iaagent.ToolProgressListener;
import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.mateu.ecdemo1.iaagent.observability.AgentObservability;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Turns each peer in an agent's configuration into a tool its model can call: {@code ask_<peer>},
 * which sends the peer a message over A2A and returns its answer.
 *
 * <p>The same move as {@link io.mateu.ecdemo1.iaagent.config.RagToolFactory}: to the model a peer is
 * one more tool, described by the peer's own catalogue description, and it decides when to
 * delegate. Built per prompt, because a call carries this prompt's bearer token and hop.
 *
 * <p>What is <em>not</em> offered, so the model cannot even try: the agent itself, an agent already
 * on this chain of calls (a cycle), and anything at all once the chain is at {@code maxDepth} or the
 * call is one that must not delegate — a guardrail's.
 */
@Component
public class PeerToolFactory {

    private static final Logger log = LoggerFactory.getLogger(PeerToolFactory.class);

    static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "message": {
                  "type": "string",
                  "description": "The request for that agent, self-contained: it does not see this conversation, only this text."
                }
              },
              "required": ["message"]
            }
            """;

    private final A2aClient client;
    private final ObservationRegistry observationRegistry;
    private final int maxDepth;
    private final ObjectMapper mapper = new ObjectMapper();

    public PeerToolFactory(A2aClient client, ObservationRegistry observationRegistry,
                           @Value("${ia.a2a.max-depth:2}") int maxDepth) {
        this.client = client;
        this.observationRegistry = observationRegistry;
        this.maxDepth = maxDepth;
    }

    public List<ToolCallback> toolsFor(AgentConfig config, String authorization, A2aHop hop) {
        return toolsFor(config, authorization, hop, ToolProgressListener.NONE);
    }

    /** As {@link #toolsFor(AgentConfig, String, A2aHop)}, with every call reported to {@code progress}. */
    public List<ToolCallback> toolsFor(AgentConfig config, String authorization, A2aHop hop,
                                       ToolProgressListener progress) {
        if (hop.noDelegation() || hop.depth() >= maxDepth) {
            return List.of();
        }
        var next = hop.next(config.agentId());
        return config.peersOrEmpty().stream()
                .filter(p -> p.id() != null && !p.id().equals(config.agentId()))
                .filter(p -> !hop.chain().contains(p.id()))
                .filter(p -> p.a2aUrl() != null && !p.a2aUrl().isBlank())
                .map(p -> (ToolCallback) new PeerToolCallback(definition(p), p, authorization, next, progress))
                .toList();
    }

    public static String toolName(String peerId) {
        return "ask_" + peerId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    /**
     * A system-prompt paragraph naming the tools, for the models that do not reach for a tool on its
     * description alone. Empty when there are none.
     */
    public static String systemContext(List<ToolCallback> peerTools) {
        if (peerTools.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("Puedes delegar en otros agentes por A2A con estas herramientas. "
                + "Úsalas cuando la petición sea de su especialidad; el otro agente no ve esta "
                + "conversación, así que envíale una petición completa y autocontenida:\n");
        peerTools.forEach(t -> sb.append("- ").append(t.getToolDefinition().name()).append('\n'));
        return sb.toString();
    }

    private static ToolDefinition definition(AgentConfig.Peer peer) {
        var description = "Asks the agent \"" + peer.name() + "\" over A2A and returns its answer. "
                + (peer.description() == null || peer.description().isBlank()
                        ? "" : "That agent: " + peer.description().trim() + " ")
                + "Use it to delegate a request that is that agent's job.";
        return DefaultToolDefinition.builder()
                .name(toolName(peer.id()))
                .description(description)
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    /** One peer. Text in, text out; a failure is a sentence the model can repeat. */
    private class PeerToolCallback implements ToolCallback {

        private final ToolDefinition definition;
        private final AgentConfig.Peer peer;
        private final String authorization;
        private final A2aHop hop;
        private final ToolProgressListener progress;

        PeerToolCallback(ToolDefinition definition, AgentConfig.Peer peer, String authorization, A2aHop hop,
                         ToolProgressListener progress) {
            this.definition = definition;
            this.peer = peer;
            this.authorization = authorization;
            this.hop = hop;
            this.progress = progress;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            var current = observationRegistry.getCurrentObservation();
            if (current != null && "spring.ai.tool".equals(current.getContext().getName())) {
                current.highCardinalityKeyValue(AgentObservability.TOOL_SOURCE, "a2a")
                        .highCardinalityKeyValue(AgentObservability.A2A_PEER, String.valueOf(peer.id()));
            }
            String message;
            try {
                message = mapper.readTree(toolInput).path("message").asText(null);
            } catch (Exception e) {
                return "The request to " + peer.name() + " was not sent: the tool input was not valid JSON.";
            }
            if (message == null || message.isBlank()) {
                return "The request to " + peer.name() + " was not sent: no message was given.";
            }
            var name = definition.name();
            var peerId = String.valueOf(peer.id());
            long started = System.nanoTime();
            ToolProgressListener.started(progress, name, peerId, "a2a");
            try {
                log.info("A2A call to {} at {} (depth {}, chain {})", peer.id(), peer.a2aUrl(),
                        hop.depth(), hop.chain());
                var reply = client.send(peer.a2aUrl(), message, authorization, hop, null);
                log.info("A2A answer from {}: {} chars", peer.id(), reply.text().length());
                ToolProgressListener.ended(progress, name, peerId, "a2a", started, null);
                return reply.text();
            } catch (A2aClient.A2aException e) {
                log.warn("A2A call to {} failed: {}", peer.id(), e.getMessage());
                ToolProgressListener.ended(progress, name, peerId, "a2a", started, e.getMessage());
                return "The agent " + peer.name() + " did not answer: " + e.getMessage();
            } catch (RuntimeException e) {
                ToolProgressListener.ended(progress, name, peerId, "a2a", started, String.valueOf(e.getMessage()));
                throw e;
            }
        }
    }
}
