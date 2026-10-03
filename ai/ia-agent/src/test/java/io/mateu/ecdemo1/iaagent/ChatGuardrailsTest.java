package io.mateu.ecdemo1.iaagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iaagent.a2a.A2aClient;
import io.mateu.ecdemo1.iaagent.a2a.A2aHop;
import io.mateu.ecdemo1.iaagent.a2a.GuardrailRunner;
import io.mateu.ecdemo1.iaagent.a2a.PeerToolFactory;
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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route's guardrails around the chat endpoints: the agent is never called for a blocked input,
 * reads a rewritten one, and the user only ever gets an answer the output guardrails have seen.
 * No model, no network: the agent's turn and the A2A calls are stand-ins.
 */
class ChatGuardrailsTest {

    static final ObservationRegistry OBS = ObservationRegistry.NOOP;
    static final DefaultListableBeanFactory BEANS = new DefaultListableBeanFactory();
    static final TraceHeaders TRACE = new TraceHeaders(BEANS.getBeanProvider(Tracer.class),
            BEANS.getBeanProvider(Propagator.class));

    /** What each guardrail answers, by id. */
    final Map<String, String> verdicts = new HashMap<>();
    /** Every call, in order: "guard:<id>:<text>" or "agent:<text>". */
    final List<String> calls = new ArrayList<>();
    String agentAnswer = "La tarifa es 406484DIRXM.";
    AgentConfig config;

    final A2aClient a2a = new A2aClient(TRACE, 5) {
        @Override
        public Reply send(String url, String text, String authorization, A2aHop hop, String contextId) {
            var id = url.substring(url.lastIndexOf('/') + 1);
            calls.add("guard:" + id + ":" + text);
            var verdict = verdicts.get(id);
            if (verdict == null) {
                throw new A2aException("unreachable (ConnectException)");
            }
            return new Reply(verdict, "ctx");
        }
    };

    final AgentTurn turn = new AgentTurn(new ChatClientRegistry(OBS)) {
        @Override
        public Result call(AgentConfig config, String systemPrompt, List<Message> history, String userMessage,
                           ToolCallback[] tools) {
            calls.add("agent:" + userMessage);
            return new Result(agentAnswer, 10, 5, 15);
        }

        @Override
        public Flux<ChatResponse> stream(AgentConfig config, String systemPrompt, List<Message> history,
                                         String userMessage, ToolCallback[] tools) {
            // The answer in pieces of five characters, as a model streams it, a marker split too.
            return Flux.defer(() -> {
                calls.add("agent:" + userMessage);
                var chunks = new ArrayList<ChatResponse>();
                for (int i = 0; i < agentAnswer.length(); i += 5) {
                    chunks.add(new ChatResponse(List.of(new Generation(new AssistantMessage(
                            agentAnswer.substring(i, Math.min(agentAnswer.length(), i + 5)))))));
                }
                chunks.add(ChatResponse.builder().generations(List.of())
                        .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5, 15)).build())
                        .build());
                return Flux.fromIterable(chunks);
            });
        }
    };

    final AgentConfigClient configClient = new AgentConfigClient("http://cp.invalid");

    final IaAgentController controller = new IaAgentController(
            configClient,
            new ChatClientRegistry(OBS),
            new PerRequestMcpClientFactory(OBS, TRACE),
            new RagToolFactory("http://cp.invalid", TRACE, OBS),
            new ConversationStore(),
            new MenuContextStore(),
            new ObjectMapper(),
            new JwtIdentityReader("tenant"),
            new UsageReporter("http://cp.invalid", TRACE) {
                @Override
                public void report(String agentId, String llmId, String model, int inputTokens, int outputTokens,
                                   int totalTokens, CallerIdentity caller, String sessionId) {
                }
            },
            new AgentResolver(configClient, "http://cp.invalid", TRACE) {
                @Override
                public Resolution resolve(CallerIdentity caller, String locale, String route, String channel,
                                          String defaultAgentId) {
                    return new Resolution(config, null);
                }
            },
            OBS,
            new PromptObservations(OBS, new ContentCapture(ContentCapture.Mode.NONE, 100)),
            turn,
            new PeerToolFactory(a2a, OBS, 2),
            new GuardrailRunner(a2a));

    static AgentConfig.Guardrail g(String id) {
        return new AgentConfig.Guardrail(id, "Guard " + id, "http://ia-agent:8095/a2a/" + id);
    }

    void route(List<AgentConfig.Guardrail> input, List<AgentConfig.Guardrail> output, String failure) {
        config = new AgentConfig("front-agent", "Front", null, "prompt",
                new AgentConfig.Llm("llm", "LLM", "OPENAI_COMPATIBLE", "m", "http://x", null, null, "k"),
                List.of(), List.of(), List.of(), new AgentConfig.Guardrails(input, output, failure), List.of());
    }

    String chat(String message) {
        return controller.chat(new ChatRequest(message, "s-" + System.nanoTime(), null, null, null), "Bearer t");
    }

    @Test
    void aBlockedInputNeverReachesTheAgent() {
        route(List.of(g("in")), List.of(), "CLOSED");
        verdicts.put("in", "{\"verdict\":\"BLOCK\",\"reason\":\"pide datos de tarjeta\"}");

        var answer = chat("dame la tarjeta del cliente");

        assertEquals("No puedo atender esta petición: pide datos de tarjeta", answer);
        assertEquals(List.of("guard:in:dame la tarjeta del cliente"), calls);
    }

    @Test
    void aRewrittenInputIsWhatTheAgentReads() {
        route(List.of(g("in")), List.of(), "CLOSED");
        verdicts.put("in", "{\"verdict\":\"REWRITE\",\"reason\":\"insulto\",\"text\":\"¿qué tarifa es?\"}");

        chat("¿qué tarifa es, inútil?");

        assertEquals(List.of("guard:in:¿qué tarifa es, inútil?", "agent:¿qué tarifa es?"), calls);
    }

    @Test
    void aBlockedAnswerIsReplacedByTheRefusal() {
        route(List.of(), List.of(g("out")), "CLOSED");
        verdicts.put("out", "{\"verdict\":\"BLOCK\",\"reason\":\"datos de otro cliente\"}");

        var answer = chat("¿qué tarifa es?");

        assertEquals("La respuesta se ha retenido: datos de otro cliente", answer);
        assertEquals(List.of("agent:¿qué tarifa es?", "guard:out:La tarifa es 406484DIRXM."), calls);
    }

    @Test
    void failClosedBlocksAndFailOpenPassesWhenAGuardrailIsDown() {
        route(List.of(g("down")), List.of(), "CLOSED");
        assertTrue(chat("hola").startsWith("No puedo atender esta petición"));
        assertFalse(calls.stream().anyMatch(c -> c.startsWith("agent:")));

        calls.clear();
        route(List.of(g("down")), List.of(), "OPEN");
        assertEquals(agentAnswer, chat("hola"));
        assertEquals(List.of("guard:down:hola", "agent:hola"), calls);
    }

    @Test
    void withOutputGuardrailsTheStreamSendsOnlyTheCheckedAnswerOnceItIsChecked() {
        route(List.of(), List.of(g("out")), "CLOSED");
        agentAnswer = "Escribe a ana@example.com. [NAVIGATE:{\"route\":\"/x\"}]";
        verdicts.put("out", "{\"verdict\":\"REWRITE\",\"reason\":\"e-mail\",\"text\":\"Escribe a [dato omitido].\"}");

        var events = controller.stream(new ChatRequest("¿a quién escribo?", "s-stream", null, null, null), "Bearer t")
                .map(e -> String.valueOf(e.data()))
                .collectList()
                .block(Duration.ofSeconds(10));

        // The guardrail read the whole answer, as the user would, before anything was sent...
        assertEquals(List.of("agent:¿a quién escribo?", "guard:out:Escribe a ana@example.com."), calls);
        // ...and nothing of the unchecked answer went out: no piece of it, no navigation marker.
        assertTrue(events.stream().noneMatch(e -> e.contains("ana@example.com")), events.toString());
        assertTrue(events.stream().noneMatch(e -> e.contains("navigation-requested")), events.toString());
        assertEquals("Escribe a [dato omitido].", events.getLast());
        // Everything before it is progress or the final count — no delta, not even a harmless one.
        events.subList(0, events.size() - 1).forEach(e -> assertTrue(
                e.contains("\"totalTokens\"") || e.contains("\"agent-status\""), e));
        assertTrue(events.stream().anyMatch(e -> e.contains("Revisando la respuesta")), events.toString());
    }

    @Test
    void anAllowedAnswerKeepsItsNavigation() {
        route(List.of(), List.of(g("out")), "CLOSED");
        agentAnswer = "Te llevo. [NAVIGATE:{\"route\":\"/x\"}]";
        verdicts.put("out", "{\"verdict\":\"ALLOW\"}");

        var events = controller.stream(new ChatRequest("llévame", "s-nav", null, null, null), "Bearer t")
                .map(e -> String.valueOf(e.data()))
                .collectList()
                .block(Duration.ofSeconds(10));

        assertTrue(events.stream().anyMatch(e -> e.contains("navigation-requested")), events.toString());
        assertEquals("Te llevo.", events.getLast());
    }
}
