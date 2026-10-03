package io.mateu.ecdemo1.iaagent;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What /stream sends while the agent works: its phases, each tool call as it starts and ends, the
 * answer in pieces — never a piece of a navigation marker — and, last, the same three things it
 * always ended with. No model, no network: the model is a stand-in that streams and calls a tool,
 * and the tool is a peer agent behind a stand-in A2A client.
 */
class ChatStreamTest {

    static final ObservationRegistry OBS = ObservationRegistry.NOOP;
    static final DefaultListableBeanFactory BEANS = new DefaultListableBeanFactory();
    static final TraceHeaders TRACE = new TraceHeaders(BEANS.getBeanProvider(Tracer.class),
            BEANS.getBeanProvider(Propagator.class));
    static final ObjectMapper JSON = new ObjectMapper();

    /** What each peer answers, by id; a missing one is unreachable. */
    final Map<String, String> peers = new HashMap<>();
    /** The model: given the tools, the chunks it streams (calling tools as it goes). */
    Function<ToolCallback[], Flux<ChatResponse>> model;
    AgentConfig config = config(List.of());

    final A2aClient a2a = new A2aClient(TRACE, 5) {
        @Override
        public Reply send(String url, String text, String authorization, A2aHop hop, String contextId) {
            var answer = peers.get(url.substring(url.lastIndexOf('/') + 1));
            if (answer == null) {
                throw new A2aException("unreachable (ConnectException)");
            }
            return new Reply(answer, "ctx");
        }
    };

    final AgentTurn turn = new AgentTurn(new ChatClientRegistry(OBS)) {
        @Override
        public Flux<ChatResponse> stream(AgentConfig config, String systemPrompt, List<Message> history,
                                         String userMessage, ToolCallback[] tools) {
            return Flux.defer(() -> model.apply(tools));
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
            JSON,
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

    static AgentConfig config(List<AgentConfig.Peer> peers) {
        return new AgentConfig("front-agent", "Front", null, "prompt",
                new AgentConfig.Llm("llm", "LLM", "OPENAI_COMPATIBLE", "m", "http://x", null, null, "k"),
                List.of(), List.of(), peers, new AgentConfig.Guardrails(List.of(), List.of(), "CLOSED"), List.of());
    }

    static ChatResponse text(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    static ChatResponse usage(int in, int out) {
        return ChatResponse.builder().generations(List.of())
                .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(in, out, in + out)).build())
                .build();
    }

    /** The data of every event, in order; keep-alive comments have none and are left out. */
    List<String> stream(String message) {
        return controller.stream(new ChatRequest(message, "s-" + System.nanoTime(), null, null, null), "Bearer t")
                .filter(e -> e.data() != null)
                .map(e -> String.valueOf(e.data()))
                .collectList()
                .block(Duration.ofSeconds(10));
    }

    static JsonNode json(String data) {
        try {
            return JSON.readTree(data);
        } catch (Exception e) {
            return null;
        }
    }

    static List<JsonNode> events(List<String> data, String event) {
        return data.stream().map(ChatStreamTest::json)
                .filter(n -> n != null && event.equals(n.path("event").asText()))
                .toList();
    }

    static String deltas(List<String> data) {
        var sb = new StringBuilder();
        events(data, "agent-delta").forEach(n -> sb.append(n.path("detail").path("text").asText()));
        return sb.toString();
    }

    @Test
    void theAnswerArrivesInPiecesAndTheCleanWholeComesLast() {
        model = tools -> Flux.just(text("Te llevo "), text("ahora. [NAVI"), text("GATE:{\"route\":"),
                text("\"/x\"}] Listo."), usage(10, 5));

        var data = stream("llévame");

        // The pieces, with the marker taken out wherever it was split...
        assertEquals("Te llevo ahora.  Listo.", deltas(data));
        assertTrue(events(data, "agent-delta").stream()
                .noneMatch(n -> n.toString().contains("NAVI") || n.toString().contains("route")), data.toString());
        // ...and the end as it always was: usage, navigation, the clean answer.
        var usage = json(data.get(data.size() - 3));
        assertNotNull(usage);
        assertEquals(15, usage.path("totalTokens").asInt());
        assertEquals("navigation-requested", json(data.get(data.size() - 2)).path("event").asText());
        assertEquals("Te llevo ahora.  Listo.", data.getLast());
    }

    @Test
    void thePhasesAreSaidBeforeTheAnswer() {
        model = tools -> Flux.just(text("Hola."), usage(1, 1));

        var data = stream("hola");

        var phases = events(data, "agent-status").stream()
                .map(n -> n.path("detail").path("phase").asText()).toList();
        assertEquals(List.of("resolving", "connecting", "thinking"), phases);
        var connected = events(data, "agent-status").get(1).path("detail");
        assertEquals(0, connected.path("tools").asInt());
        int firstDelta = data.indexOf(data.stream().filter(d -> d.contains("agent-delta")).findFirst().orElseThrow());
        int thinking = data.indexOf(data.stream().filter(d -> d.contains("\"thinking\"")).findFirst().orElseThrow());
        assertTrue(thinking < firstDelta, data.toString());
    }

    @Test
    void aToolCallIsReportedAsItStartsAndEndsAndTheAnswerIsWhatCameAfterIt() {
        config = config(List.of(new AgentConfig.Peer("ventas", "Ventas", "vende", "http://ia-agent:8095/a2a/ventas")));
        peers.put("ventas", "3 reservas");
        model = tools -> Flux.concat(
                Flux.just(text("Voy a preguntar.")),
                Flux.defer(() -> Flux.just(text(tools[0].call("{\"message\":\"¿cuántas?\"}")))).filter(r -> false),
                Flux.just(text("Tienes "), text("3 reservas."), usage(20, 8)));

        var data = stream("¿cuántas reservas?");

        var toolEvents = events(data, "agent-tool");
        assertEquals(2, toolEvents.size(), data.toString());
        var start = toolEvents.get(0).path("detail");
        var end = toolEvents.get(1).path("detail");
        assertEquals("ask_ventas", start.path("name").asText());
        assertEquals("ventas", start.path("server").asText());
        assertEquals("a2a", start.path("kind").asText());
        assertEquals("start", start.path("phase").asText());
        assertEquals("end", end.path("phase").asText());
        assertTrue(end.has("ms"));
        assertFalse(end.has("error"));
        // Two rounds, two paragraphs on screen; the answer is the last round, as /chat's is.
        assertEquals("Voy a preguntar.\n\nTienes 3 reservas.", deltas(data));
        assertEquals("Tienes 3 reservas.", data.getLast());
    }

    @Test
    void aFailedToolSaysWhy() {
        config = config(List.of(new AgentConfig.Peer("caido", "Caído", null, "http://ia-agent:8095/a2a/caido")));
        model = tools -> Flux.defer(() -> {
            tools[0].call("{\"message\":\"hola\"}");
            return Flux.just(text("No he podido."), usage(1, 1));
        });

        var data = stream("pregúntale");

        var end = events(data, "agent-tool").getLast().path("detail");
        assertEquals("end", end.path("phase").asText());
        assertTrue(end.path("error").asText().contains("unreachable"), end.toString());
    }

    @Test
    void aModelFailureEndsTheStreamWithAnErrorEvent() {
        model = tools -> Flux.concat(Flux.just(text("Empie")), Flux.error(new IllegalStateException("se cayó")));

        var data = stream("hola");

        var last = json(data.getLast());
        assertEquals("agent-error", last.path("event").asText());
        assertTrue(last.path("detail").path("message").asText().contains("se cayó"));
    }

    @Test
    void theJsonEventsAreOneLineEach() {
        model = tools -> Flux.just(text("línea uno\nlínea dos"), usage(1, 1));

        var data = stream("hola");

        // A multi-line delta is still one data line: its newline travels escaped inside the JSON,
        // so a client that splits by line never cuts one in half.
        events(data, "agent-delta").forEach(n -> assertFalse(n.toString().contains("\n")));
        assertEquals("línea uno\nlínea dos", deltas(data));
        data.stream().filter(d -> d.startsWith("{")).forEach(d -> assertFalse(d.contains("\n"), d));
    }
}
