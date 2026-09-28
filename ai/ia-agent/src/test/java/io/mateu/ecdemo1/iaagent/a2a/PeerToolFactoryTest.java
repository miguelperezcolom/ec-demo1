package io.mateu.ecdemo1.iaagent.a2a;

import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One tool per peer an agent may call — and none where a call would be refused anyway. */
class PeerToolFactoryTest {

    record Sent(String url, String text, String authorization, A2aHop hop) {}

    final List<Sent> sent = new ArrayList<>();

    final A2aClient client = new A2aClient(null, 5) {
        @Override
        public Reply send(String url, String text, String authorization, A2aHop hop, String contextId) {
            sent.add(new Sent(url, text, authorization, hop));
            if (url.endsWith("/down")) {
                throw new A2aException("unreachable (ConnectException)");
            }
            return new Reply("B says hi", "ctx");
        }
    };

    final PeerToolFactory factory = new PeerToolFactory(client, ObservationRegistry.NOOP, 2);

    static AgentConfig.Peer peer(String id) {
        return new AgentConfig.Peer(id, "Agent " + id, "Handles " + id, "http://ia-agent:8095/a2a/" + id);
    }

    static AgentConfig agent(String id, AgentConfig.Peer... peers) {
        return new AgentConfig(id, "Agent " + id, null, "p", null, List.of(), List.of(), List.of(peers), null, List.of());
    }

    static List<String> names(List<ToolCallback> tools) {
        return tools.stream().map(t -> t.getToolDefinition().name()).toList();
    }

    @Test
    void eachPeerIsOneToolDescribedByThePeer() {
        var tools = factory.toolsFor(agent("a", peer("b"), peer("mapping-agent")), "Bearer t", A2aHop.origin());
        assertEquals(List.of("ask_b", "ask_mapping_agent"), names(tools));
        var description = tools.getFirst().getToolDefinition().description();
        assertTrue(description.contains("Agent b"), description);
        assertTrue(description.contains("Handles b"), description);
        assertTrue(tools.getFirst().getToolDefinition().inputSchema().contains("\"message\""));
    }

    @Test
    void noToolForItselfNorForAnAgentAlreadyOnTheChain() {
        var tools = factory.toolsFor(agent("a", peer("a"), peer("b"), peer("c")), "Bearer t",
                new A2aHop(1, List.of("c"), false));
        assertEquals(List.of("ask_b"), names(tools));
    }

    @Test
    void noToolsAtTheDepthLimitOrWhenTheCallMustNotDelegate() {
        assertTrue(factory.toolsFor(agent("a", peer("b")), "Bearer t", new A2aHop(2, List.of(), false)).isEmpty());
        assertTrue(factory.toolsFor(agent("a", peer("b")), "Bearer t", A2aHop.origin().withoutDelegation()).isEmpty());
    }

    @Test
    void callingTheToolSendsTheMessageWithTheCallersTokenOneHopDeeper() {
        var tool = factory.toolsFor(agent("a", peer("b")), "Bearer t", A2aHop.origin()).getFirst();

        var answer = tool.call("{\"message\":\"¿qué tarifa es DIRXM?\"}");

        assertEquals("B says hi", answer);
        var call = sent.getFirst();
        assertEquals("http://ia-agent:8095/a2a/b", call.url());
        assertEquals("¿qué tarifa es DIRXM?", call.text());
        assertEquals("Bearer t", call.authorization());
        assertEquals(1, call.hop().depth());
        assertEquals(List.of("a"), call.hop().chain());
    }

    @Test
    void aFailedCallIsASentenceForTheModelNotAnException() {
        var tool = factory.toolsFor(agent("a", peer("down")), "Bearer t", A2aHop.origin()).getFirst();
        var answer = tool.call("{\"message\":\"hola\"}");
        assertTrue(answer.startsWith("The agent Agent down did not answer"), answer);
        assertTrue(tool.call("{}").contains("no message"));
    }
}
