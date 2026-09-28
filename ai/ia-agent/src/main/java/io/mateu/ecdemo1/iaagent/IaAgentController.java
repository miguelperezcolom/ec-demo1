package io.mateu.ecdemo1.iaagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iaagent.a2a.A2aHop;
import io.mateu.ecdemo1.iaagent.a2a.GuardrailRunner;
import io.mateu.ecdemo1.iaagent.a2a.PeerToolFactory;
import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.mateu.ecdemo1.iaagent.config.AgentConfigClient;
import io.mateu.ecdemo1.iaagent.config.AgentResolver;
import io.mateu.ecdemo1.iaagent.config.ChatClientRegistry;
import io.mateu.ecdemo1.iaagent.config.RagToolFactory;
import io.mateu.ecdemo1.iaagent.observability.PromptObservations;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/ai/api/agent")
public class IaAgentController {

    private static final Logger log = LoggerFactory.getLogger(IaAgentController.class);

    /** Matches [NAVIGATE:{...}] blocks emitted by the LLM anywhere in its response. */
    private static final Pattern NAVIGATE_PATTERN =
            Pattern.compile("\\[NAVIGATE:(\\{[^]]*})]", Pattern.DOTALL);

    private final AgentConfigClient configClient;
    private final ChatClientRegistry chatClients;
    private final PerRequestMcpClientFactory mcpFactory;
    private final RagToolFactory ragTools;
    private final ConversationStore conversationStore;
    private final MenuContextStore menuContextStore;
    private final ObjectMapper objectMapper;
    private final io.mateu.ecdemo1.iaagent.identity.JwtIdentityReader jwtIdentityReader;
    private final io.mateu.ecdemo1.iaagent.usage.UsageReporter usageReporter;
    private final AgentResolver agentResolver;
    private final ObservationRegistry observationRegistry;
    private final PromptObservations prompts;
    private final AgentTurn agentTurn;
    private final PeerToolFactory peerTools;
    private final GuardrailRunner guardrails;

    public IaAgentController(AgentConfigClient configClient,
                             ChatClientRegistry chatClients,
                             PerRequestMcpClientFactory mcpFactory,
                             RagToolFactory ragTools,
                             ConversationStore conversationStore,
                             MenuContextStore menuContextStore,
                             ObjectMapper objectMapper,
                             io.mateu.ecdemo1.iaagent.identity.JwtIdentityReader jwtIdentityReader,
                             io.mateu.ecdemo1.iaagent.usage.UsageReporter usageReporter,
                             AgentResolver agentResolver,
                             ObservationRegistry observationRegistry,
                             PromptObservations prompts,
                             AgentTurn agentTurn,
                             PeerToolFactory peerTools,
                             GuardrailRunner guardrails) {
        this.configClient = configClient;
        this.chatClients = chatClients;
        this.mcpFactory = mcpFactory;
        this.ragTools = ragTools;
        this.conversationStore = conversationStore;
        this.menuContextStore = menuContextStore;
        this.objectMapper = objectMapper;
        this.jwtIdentityReader = jwtIdentityReader;
        this.usageReporter = usageReporter;
        this.agentResolver = agentResolver;
        this.observationRegistry = observationRegistry;
        this.prompts = prompts;
        this.agentTurn = agentTurn;
        this.peerTools = peerTools;
        this.guardrails = guardrails;
    }

    // ── Observation ──────────────────────────────────────────────────────────

    /** See {@link PromptObservations}: shared with the A2A endpoint. */
    private Observation startPromptObservation(Observation parent, String sessionId, String userMessage) {
        return prompts.start(parent, sessionId, userMessage, A2aHop.origin().depth());
    }

    private void tagResponse(Observation observation, String response) {
        prompts.tagResponse(observation, response);
    }

    private static void tagAgent(Observation observation, AgentConfig config) {
        PromptObservations.tagAgent(observation, config);
    }

    private static void tagTools(Observation observation, PerRequestMcpClientFactory.PerRequestTools tools,
                                 int toolCount) {
        PromptObservations.tagTools(observation, toolCount, tools.expectedServers(), tools.connectedServers());
    }

    private static void outcome(Observation observation, String outcome) {
        PromptObservations.outcome(observation, outcome);
    }

    /**
     * Raised when this pod has no configuration to answer with at all — the control plane has
     * never been reachable, or it refuses to serve this agent. Readiness reports the same thing,
     * so a prompt should not normally get this far; when one does, the message is the diagnosis.
     */
    private static class NoConfigurationException extends RuntimeException {
        NoConfigurationException(String message) { super(message); }
    }

    /**
     * Every tool this agent has, in one list: the MCP servers', the RAG sources' and one per peer
     * agent it may call over A2A. The model sees one list, and nothing downstream has to know which
     * kind a call belongs to.
     */
    private List<org.springframework.ai.tool.ToolCallback> allTools(
            AgentConfig config, PerRequestMcpClientFactory.PerRequestTools mcp,
            List<org.springframework.ai.tool.ToolCallback> peers) {
        var all = new ArrayList<org.springframework.ai.tool.ToolCallback>(
                List.of(mcp.getCallbacks()));
        all.addAll(ragTools.toolsFor(config.rags()));
        all.addAll(peers);
        return all;
    }

    /**
     * The configuration for this prompt, chosen by context. The caller's identity is read from the
     * bearer token and posted to the control plane, which routes to an agent and refuses an
     * over-budget one — a refusal arrives here as {@link NoConfigurationException} and reaches the
     * panel as its message, the same path a missing configuration already took.
     *
     * <p>Locale and the current screen come from the request when the chat client sends them, and
     * are null when it does not — so routing rules that key on a role or a tenant work regardless,
     * and rules that key on a screen or a locale start working the moment the frontend carries
     * those fields, with no change here.
     */
    private AgentConfig resolveConfig(String authorization, ChatRequest request) {
        var caller = jwtIdentityReader.read(authorization);
        var resolution = agentResolver.resolve(caller, request.locale(), request.currentRoute());
        if (!resolution.allowed()) {
            throw new NoConfigurationException(resolution.deniedReason());
        }
        return resolution.config();
    }

    // ── Guardrails ───────────────────────────────────────────────────────────

    /**
     * The route's input guardrails, over A2A, before the agent sees anything: the text to answer
     * — the user's, or a guardrail's rewrite of it — or a refusal, recorded on the observation.
     */
    private GuardrailRunner.Outcome checkInput(Observation observation, AgentConfig config, String message,
                                               String authorization) {
        var outcome = guardrails.check(GuardrailRunner.Side.INPUT, config, message, authorization, A2aHop.origin());
        if (outcome.blocked()) {
            var refusal = GuardrailRunner.refusal(GuardrailRunner.Side.INPUT, outcome);
            tagResponse(observation, refusal);
            outcome(observation, "blocked_input");
        }
        return outcome;
    }

    /**
     * The route's output guardrails on the whole answer, before any of it leaves: what the user
     * gets. This is why an answer is never sent in pieces while a route has output guardrails — a
     * token already on the wire cannot be taken back. (Neither endpoint streams tokens today: /stream
     * sends placeholders while the agent works and the answer in one event, after this.)
     *
     * <p>They read the answer as the user would, without navigation markers; one they allow
     * unchanged keeps its markers, one they rewrite loses them.
     */
    private Checked checkOutput(AgentConfig config, String raw, String authorization) {
        if (config.guardrailsOrNone().output().isEmpty()) {
            return new Checked(raw, false);
        }
        var clean = parseNavigation(raw).cleanText();
        var outcome = guardrails.check(GuardrailRunner.Side.OUTPUT, config, clean, authorization, A2aHop.origin());
        if (outcome.blocked()) {
            return new Checked(GuardrailRunner.refusal(GuardrailRunner.Side.OUTPUT, outcome), true);
        }
        return new Checked(outcome.text().equals(clean) ? raw : outcome.text(), false);
    }

    /** The answer the user gets, and whether it is a refusal in place of the agent's. */
    private record Checked(String text, boolean blocked) {
        String outcome() {
            return blocked ? "blocked_output" : "success";
        }
    }

    // ── Internal types ───────────────────────────────────────────────────────

    private record LlmResult(String content, int inputTokens, int outputTokens, int totalTokens) {}

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildSystemPrompt(String basePrompt, String serverContext, String sessionId,
                                     List<org.springframework.ai.tool.ToolCallback> peers) {
        var sb = new StringBuilder(basePrompt);
        if (serverContext != null && !serverContext.isBlank()) {
            sb.append("\n\nContexto de las herramientas disponibles:\n\n").append(serverContext);
        }
        var peerContext = PeerToolFactory.systemContext(peers);
        if (!peerContext.isBlank()) {
            sb.append("\n\n").append(peerContext);
        }
        String menuPrompt = menuContextStore.buildMenuSystemPrompt(sessionId);
        if (!menuPrompt.isBlank()) {
            sb.append("\n\n").append(menuPrompt);
        }
        return sb.toString();
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }

    private ServerSentEvent<String> tokenEvent(int input, int output, int total) {
        return ServerSentEvent.<String>builder()
                .data("{\"inputTokens\":" + input
                        + ",\"outputTokens\":" + output
                        + ",\"totalTokens\":" + total + "}")
                .build();
    }

    private ServerSentEvent<String> contentEvent(String text) {
        return ServerSentEvent.<String>builder().data(text).build();
    }

    private ServerSentEvent<String> errorEvent(Throwable e) {
        String message = e.getClass().getSimpleName() + ": " + e.getMessage();
        try {
            String json = objectMapper.writeValueAsString(
                    java.util.Map.of("event", "agent-error", "detail", java.util.Map.of("message", message)));
            return ServerSentEvent.<String>builder().data(json).build();
        } catch (Exception ex) {
            return ServerSentEvent.<String>builder()
                    .data("{\"event\":\"agent-error\",\"detail\":{\"message\":\"Error interno\"}}")
                    .build();
        }
    }

    /**
     * Scans {@code rawText} for [NAVIGATE:{...}] markers, builds an SSE navigation
     * event for each one, and returns the text with all markers stripped.
     */
    private record ParsedResponse(String cleanText, List<ServerSentEvent<String>> navEvents) {}

    private ParsedResponse parseNavigation(String rawText) {
        var navEvents = new ArrayList<ServerSentEvent<String>>();
        Matcher m = NAVIGATE_PATTERN.matcher(rawText);
        while (m.find()) {
            String json = m.group(1);
            try {
                // Validate JSON is parseable before emitting
                objectMapper.readTree(json);
                String ssePayload = "{\"event\":\"navigation-requested\",\"detail\":" + json + "}";
                navEvents.add(ServerSentEvent.<String>builder().data(ssePayload).build());
                log.debug("Navigation requested: {}", json);
            } catch (Exception e) {
                log.warn("Malformed NAVIGATE block, ignoring: {}", json);
            }
        }
        String cleanText = NAVIGATE_PATTERN.matcher(rawText).replaceAll("").trim();
        return new ParsedResponse(cleanText, navEvents);
    }

    // ── /chat  (POST, non-streaming) ─────────────────────────────────────────

    @PostMapping(value = "/chat", produces = "text/plain;charset=UTF-8")
    public String chat(@RequestBody ChatRequest request,
                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        String sessionId = request.sessionId();
        // What happened goes to the log; what was said goes to the trace, and only when
        // IA_CAPTURE_CONTENT says so. The text itself is here at DEBUG, for a local run.
        log.info("Chat request session={}: {} chars", sessionId, length(request.message()));
        log.debug("Chat request session={}: '{}'", sessionId, request.message());
        menuContextStore.update(sessionId, request.menuContext());

        var observation = startPromptObservation(observationRegistry.getCurrentObservation(), sessionId,
                request.message());
        try (var scope = observation.openScope()) {
            // Resolved before anything else: it decides the agent (by the caller's context), the
            // model, the credential, the prompt and which MCP servers to even open a connection to
            // — and refuses an over-budget request here rather than after spending on it.
            AgentConfig config = resolveConfig(authorization, request);
            tagAgent(observation, config);

            var input = checkInput(observation, config, request.message(), authorization);
            if (input.blocked()) {
                return GuardrailRunner.refusal(GuardrailRunner.Side.INPUT, input);
            }
            String message = input.text();

            try (var tools = mcpFactory.createTools(config.mcpUrls(), authorization)) {
                var peers = peerTools.toolsFor(config, authorization, A2aHop.origin());
                var toolCallbacks = allTools(config, tools, peers);
                tagTools(observation, tools, toolCallbacks.size());
                // Only a hard stop when there is nothing at all to call. An agent whose MCP
                // servers are all down but which still has a RAG source or a peer agent can
                // answer through those, and refusing here would take that away.
                if (tools.hasNoServers() && config.rags().isEmpty() && peers.isEmpty()) {
                    String err = "No hay ningún servidor MCP disponible (" + tools.expectedServers()
                            + " configurados, 0 conectados) ni ninguna fuente RAG. No puedo "
                            + "responder sin acceso a las herramientas.";
                    log.warn("Chat aborted session={}: {}", sessionId, err);
                    tagResponse(observation, err);
                    outcome(observation, "no_tools");
                    return err;
                }
                String systemPrompt = buildSystemPrompt(config.systemPrompt(),
                        tools.getServerSystemContext(), sessionId, peers);
                var history = conversationStore.getHistory(sessionId);

                var turn = agentTurn.call(config, systemPrompt, history, message,
                        toolCallbacks.toArray(new org.springframework.ai.tool.ToolCallback[0]));
                String content = turn.content();
                int inputTokens = turn.inputTokens(), outputTokens = turn.outputTokens(),
                        totalTokens = turn.totalTokens();
                log.info("Chat response session={}: {} chars, tokens={}/{}/{}",
                        sessionId, content != null ? content.length() : 0,
                        inputTokens, outputTokens, totalTokens);

                String raw = (content != null && !content.isBlank()) ? content : "(sin respuesta)";
                var checked = checkOutput(config, raw, authorization);
                String result = parseNavigation(checked.text()).cleanText();
                conversationStore.addExchange(sessionId, message, result);
                conversationStore.accumulateTokens(sessionId, inputTokens, outputTokens, totalTokens);
                usageReporter.report(config.agentId(), config.llm().id(), config.llm().model(),
                        inputTokens, outputTokens, totalTokens,
                        jwtIdentityReader.read(authorization), sessionId);
                tagResponse(observation, result);
                outcome(observation, checked.outcome());
                return result;
            }
        } catch (NoConfigurationException | ChatClientRegistry.UnsupportedProviderException e) {
            // Not an error during the prompt — a misconfiguration. The message is written for
            // whoever can fix it, so it is returned as it is rather than wrapped in a class name.
            log.warn("Chat aborted session={}: {}", sessionId, e.getMessage());
            tagResponse(observation, e.getMessage());
            outcome(observation, "refused");
            return e.getMessage();
        } catch (Exception e) {
            log.error("Error en chat session={} — {}: {}", sessionId, e.getClass().getName(), e.getMessage(), e);
            observation.error(e);
            return "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            observation.stop();
        }
    }

    // ── /stream  (POST, SSE) ─────────────────────────────────────────────────

    /**
     * SSE endpoint.
     *
     * Request body: {@link ChatRequest} (JSON) — includes {@code message}, {@code sessionId}
     * and optionally {@code menuContext} (only needs to be sent when the menu changes).
     *
     * SSE events emitted:
     * <ul>
     *   <li>{@code data: {"inputTokens":N,"outputTokens":M,"totalTokens":T}} — token usage
     *       (placeholder every 2 s while the LLM is running, then the real counts)</li>
     *   <li>{@code data: {"event":"navigation-requested","detail":{...}}} — navigation command
     *       (emitted if the LLM included a [NAVIGATE:{...}] marker in its response)</li>
     *   <li>{@code data: <text>} — the actual response text</li>
     * </ul>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest request,
                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        String sessionId = request.sessionId();
        log.info("Stream request session={}: {} chars", sessionId, length(request.message()));
        log.debug("Stream request session={}: '{}'", sessionId, request.message());

        // Cache menu if provided
        menuContextStore.update(sessionId, request.menuContext());

        var history = conversationStore.getHistory(sessionId);

        // Taken here, on the request's thread: the prompt runs on another, which does not inherit
        // it, and without it the prompt's span would start a trace of its own.
        Observation requestObservation = observationRegistry.getCurrentObservation();

        // Blocking LLM call on a dedicated thread; cache() so both subscribers share the result.
        Mono<LlmResult> resultMono = Mono.fromCallable(() -> {
                    var observation = startPromptObservation(requestObservation, sessionId, request.message());
                    try (var scope = observation.openScope()) {
                        return streamPrompt(observation, request, authorization, sessionId, history);
                    } catch (NoConfigurationException | ChatClientRegistry.UnsupportedProviderException e) {
                        tagResponse(observation, e.getMessage());
                        outcome(observation, "refused");
                        throw e;
                    } catch (Exception e) {
                        observation.error(e);
                        throw e;
                    } finally {
                        observation.stop();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .cache();

        // Periodic token-usage placeholders while the LLM is running.
        // onErrorComplete() so that an LLM error terminates the interval cleanly
        // without propagating through the concat.
        Flux<ServerSentEvent<String>> periodicTokens = Flux
                .interval(Duration.ZERO, Duration.ofSeconds(2))
                .map(i -> tokenEvent(0, 0, 0))
                .takeUntilOther(resultMono.onErrorComplete());

        // Final events: real token counts + optional navigation events + content text.
        // On error, emit a structured agent-error SSE event so the client can display
        // the actual cause (e.g. missing LLM API key).
        Flux<ServerSentEvent<String>> finalEvents = resultMono
                // A misconfiguration is not a stack trace: it is a sentence for whoever can fix
                // it, and it reaches the panel as the answer rather than as an error event.
                .onErrorResume(e -> e instanceof NoConfigurationException
                                || e instanceof ChatClientRegistry.UnsupportedProviderException,
                        e -> {
                            log.warn("Stream aborted session={}: {}", sessionId, e.getMessage());
                            return Mono.just(new LlmResult(e.getMessage(), 0, 0, 0));
                        })
                .doOnError(e -> log.error("Stream error session={} — {}: {}",
                        sessionId, e.getClass().getName(), e.getMessage(), e))
                .flatMapMany(r -> {
                    var parsed = parseNavigation(r.content());
                    var events = new ArrayList<ServerSentEvent<String>>();
                    events.add(tokenEvent(r.inputTokens(), r.outputTokens(), r.totalTokens()));
                    events.addAll(parsed.navEvents());
                    events.add(contentEvent(parsed.cleanText()));
                    return Flux.fromIterable(events);
                })
                .onErrorResume(e -> Flux.just(errorEvent(e)));

        return Flux.concat(periodicTokens, finalEvents);
    }

    /** The body of one /stream prompt, inside its observation. */
    private LlmResult streamPrompt(Observation observation, ChatRequest request, String authorization,
                                   String sessionId,
                                   List<org.springframework.ai.chat.messages.Message> history) {
        // Same order as /chat: resolve first, because it decides which agent, which
        // servers to connect to and with which model to answer — and can refuse.
        AgentConfig config = resolveConfig(authorization, request);
        tagAgent(observation, config);
        var input = checkInput(observation, config, request.message(), authorization);
        if (input.blocked()) {
            return new LlmResult(GuardrailRunner.refusal(GuardrailRunner.Side.INPUT, input), 0, 0, 0);
        }
        String message = input.text();
        try (var tools = mcpFactory.createTools(config.mcpUrls(), authorization)) {
            var peers = peerTools.toolsFor(config, authorization, A2aHop.origin());
            var toolCallbacks = allTools(config, tools, peers);
            tagTools(observation, tools, toolCallbacks.size());
            if (tools.hasNoServers() && config.rags().isEmpty() && peers.isEmpty()) {
                String err = "No hay ningún servidor MCP disponible ("
                        + tools.expectedServers() + " configurados, 0 conectados) ni "
                        + "ninguna fuente RAG. No puedo responder sin acceso a las "
                        + "herramientas.";
                log.warn("Stream aborted session={}: no tools at all", sessionId);
                tagResponse(observation, err);
                outcome(observation, "no_tools");
                return new LlmResult(err, 0, 0, 0);
            }
            String systemPrompt = buildSystemPrompt(config.systemPrompt(),
                    tools.getServerSystemContext(), sessionId, peers);
            var turn = agentTurn.call(config, systemPrompt, history, message,
                    toolCallbacks.toArray(new org.springframework.ai.tool.ToolCallback[0]));
            String content = turn.content();
            int inputTokens = turn.inputTokens(), outputTokens = turn.outputTokens(),
                    totalTokens = turn.totalTokens();

            log.info("Stream completed session={}: {} chars, tokens={}/{}/{}",
                    sessionId, content != null ? content.length() : 0,
                    inputTokens, outputTokens, totalTokens);

            var checked = checkOutput(config,
                    (content != null && !content.isBlank()) ? content : "(sin respuesta)", authorization);
            String raw = checked.text();
            conversationStore.addExchange(sessionId, message, raw);
            conversationStore.accumulateTokens(sessionId, inputTokens, outputTokens, totalTokens);
            usageReporter.report(config.agentId(), config.llm().id(), config.llm().model(),
                    inputTokens, outputTokens, totalTokens,
                    jwtIdentityReader.read(authorization), sessionId);
            tagResponse(observation, raw);
            outcome(observation, checked.outcome());
            int[] cumulative = conversationStore.getTotalTokens(sessionId);
            return new LlmResult(raw, cumulative[0], cumulative[1], cumulative[2]);
        }
    }
}
