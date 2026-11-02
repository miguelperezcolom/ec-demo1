package io.mateu.ecdemo1.iaagent.a2a;

import io.mateu.ecdemo1.iaagent.AgentTurn;
import io.mateu.ecdemo1.iaagent.ConversationStore;
import io.mateu.ecdemo1.iaagent.PerRequestMcpClientFactory;
import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.mateu.ecdemo1.iaagent.config.AgentConfigClient;
import io.mateu.ecdemo1.iaagent.config.AgentResolver;
import io.mateu.ecdemo1.iaagent.config.ChatClientRegistry;
import io.mateu.ecdemo1.iaagent.config.RagToolFactory;
import io.mateu.ecdemo1.iaagent.identity.CallerIdentity;
import io.mateu.ecdemo1.iaagent.identity.JwtIdentityReader;
import io.mateu.ecdemo1.iaagent.observability.ContentCapture;
import io.mateu.ecdemo1.iaagent.observability.PromptObservations;
import io.mateu.ecdemo1.iaagent.observability.TraceHeaders;
import io.mateu.ecdemo1.iaagent.usage.UsageReporter;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Budgets and guardrails on the way in over A2A: a call from outside the agents is checked as a chat
 * prompt is, a spent budget or a blocked text is an error to the caller, and a blocked answer is
 * still charged. No model, no network: the agent's turn and the A2A calls are stand-ins.
 */
class A2aGovernanceTest {

    static final ObservationRegistry OBS = ObservationRegistry.NOOP;
    static final DefaultListableBeanFactory BEANS = new DefaultListableBeanFactory();
    static final TraceHeaders TRACE = new TraceHeaders(BEANS.getBeanProvider(Tracer.class),
            BEANS.getBeanProvider(Propagator.class));

    final Map<String, String> verdicts = new HashMap<>();
    final List<String> calls = new ArrayList<>();
    final List<String> charged = new ArrayList<>();

    final A2aClient a2a = new A2aClient(TRACE, 5) {
        @Override
        public Reply send(String url, String text, String authorization, A2aHop hop, String contextId) {
            var id = url.substring(url.lastIndexOf('/') + 1);
            calls.add("guard:" + id + ":" + text);
            return new Reply(verdicts.get(id), "ctx");
        }
    };

    final AgentTurn turn = new AgentTurn(new ChatClientRegistry(OBS)) {
        @Override
        public Result call(AgentConfig config, String systemPrompt, List<Message> history, String userMessage,
                           ToolCallback[] tools) {
            calls.add("agent:" + userMessage);
            return new Result("la respuesta", 10, 5, 15);
        }
    };

    final A2aAgentExecutor executor = new A2aAgentExecutor(
            new PerRequestMcpClientFactory(OBS, TRACE),
            new RagToolFactory("http://cp.invalid", TRACE, OBS),
            new PeerToolFactory(a2a, OBS, 2),
            turn,
            new ConversationStore(),
            new UsageReporter("http://cp.invalid", TRACE) {
                @Override
                public void report(String agentId, String llmId, String model, int inputTokens, int outputTokens,
                                   int totalTokens, CallerIdentity caller, String sessionId) {
                    charged.add(agentId + ":" + totalTokens);
                }
            },
            new JwtIdentityReader("tenant"),
            new PromptObservations(OBS, new ContentCapture(ContentCapture.Mode.NONE, 100)),
            OBS,
            new GuardrailRunner(a2a));

    static AgentConfig guarded(String input, String output) {
        return new AgentConfig("intranet-agent", "Intranet", null, "prompt",
                new AgentConfig.Llm("llm", "LLM", "OPENAI_COMPATIBLE", "m", "http://x", null, null, "k"),
                List.of(), List.of(), List.of(),
                new AgentConfig.Guardrails(
                        input == null ? List.of() : List.of(g(input)),
                        output == null ? List.of() : List.of(g(output)), "CLOSED"),
                List.of());
    }

    static AgentConfig.Guardrail g(String id) {
        return new AgentConfig.Guardrail(id, "Guard " + id, "http://ia-agent:8095/a2a/" + id);
    }

    static final A2aHop FROM_OUTSIDE = A2aHop.fromHeaders(null, null, null);

    String run(AgentConfig config, A2aHop hop) {
        return executor.run(config, "hola", "ctx-" + System.nanoTime(), "Bearer t", hop);
    }

    @Test
    void aBlockedInputNeverReachesTheAgentAndIsNotCharged() {
        verdicts.put("in", "{\"verdict\":\"BLOCK\",\"reason\":\"pide datos de tarjeta\"}");

        var refusal = assertThrows(A2aServer.Blocked.class, () -> run(guarded("in", null), FROM_OUTSIDE));

        assertEquals("No puedo atender esta petición: pide datos de tarjeta", refusal.getMessage());
        assertEquals(List.of("guard:in:hola"), calls);
        assertTrue(charged.isEmpty());
    }

    @Test
    void theAgentReadsARewrittenInput() {
        verdicts.put("in", "{\"verdict\":\"REWRITE\",\"reason\":\"insulto\",\"text\":\"buenas\"}");

        assertEquals("la respuesta", run(guarded("in", null), FROM_OUTSIDE));
        assertEquals(List.of("guard:in:hola", "agent:buenas"), calls);
    }

    @Test
    void aBlockedAnswerIsAnErrorAndIsStillCharged() {
        verdicts.put("out", "{\"verdict\":\"BLOCK\",\"reason\":\"datos de otra persona\"}");

        var refusal = assertThrows(A2aServer.Blocked.class, () -> run(guarded(null, "out"), FROM_OUTSIDE));

        assertEquals("La respuesta se ha retenido: datos de otra persona", refusal.getMessage());
        assertEquals(List.of("intranet-agent:15"), charged);
    }

    @Test
    void aPeerCallIsInsideThePerimeter() {
        var answer = run(guarded("in", "out"), A2aHop.fromHeaders("1", "front-agent", null));

        assertEquals("la respuesta", answer);
        assertEquals(List.of("agent:hola"), calls);
    }

    @Test
    void aGuardrailsOwnCallRunsNoGuardrails() {
        var answer = run(guarded("in", "out"), A2aHop.fromHeaders("1", null, "true"));

        assertEquals("la respuesta", answer);
        assertEquals(List.of("agent:hola"), calls);
    }

    @Test
    void aSpentBudgetReachesTheCallerAsAnError() throws Exception {
        var configClient = new AgentConfigClient("http://cp.invalid");
        var controller = new A2aController(configClient,
                new AgentResolver(configClient, "http://cp.invalid", TRACE) {
                    @Override
                    public Resolution resolveNamed(String agentId, CallerIdentity caller) {
                        return new Resolution(null, "Budget 'daily' is spent");
                    }
                },
                new JwtIdentityReader("tenant"), executor, 2, "http://ia-agent:8095", true);

        var response = controller.jsonRpc("intranet-agent", """
                {"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{
                  "kind":"message","role":"user","messageId":"m","parts":[{"kind":"text","text":"hola"}]}}}
                """, "Bearer t", null, null, null);

        var body = response.getBody();
        assertTrue(body.contains(String.valueOf(A2aServer.AGENT_UNAVAILABLE)), body);
        assertTrue(body.contains("Budget 'daily' is spent"), body);
        assertFalse(calls.stream().anyMatch(c -> c.startsWith("agent:")));
    }
}
