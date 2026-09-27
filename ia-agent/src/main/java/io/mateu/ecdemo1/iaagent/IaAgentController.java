package io.mateu.ecdemo1.iaagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.mateu.ecdemo1.iaagent.config.AgentConfigClient;
import io.mateu.ecdemo1.iaagent.config.AgentResolver;
import io.mateu.ecdemo1.iaagent.config.ChatClientRegistry;
import io.mateu.ecdemo1.iaagent.config.RagToolFactory;
import io.mateu.ecdemo1.iaagent.observability.AgentObservability;
import io.mateu.ecdemo1.iaagent.observability.ContentCapture;
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
    private final ContentCapture content;

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
                             ContentCapture content) {
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
        this.content = content;
    }

    // ── Observation ──────────────────────────────────────────────────────────

    /**
     * The {@code invoke_agent} span and the {@code ia_agent_prompt_seconds} timer: one per prompt,
     * parent of every model round trip and tool call it causes. {@code parent} is passed in rather
     * than taken from the thread because /stream answers on another one than the request's.
     *
     * <p>Every low-cardinality key is set at the start, with a placeholder, and overwritten as it
     * becomes known: they are Prometheus labels, and a meter must carry the same ones on every
     * sample.
     *
     * <p>The tokens, model calls and tool calls it ends with are not set here: every model round
     * trip and tool call under it adds itself up in {@link AgentObservability.PromptStats} as it
     * stops, and {@link AgentObservability} writes the totals when this one does. The user's
     * message goes on it only when {@link ContentCapture} records content.
     */
    private Observation startPromptObservation(Observation parent, String sessionId, String userMessage) {
        var observation = Observation.createNotStarted(AgentObservability.PROMPT, observationRegistry)
                .parentObservation(parent)
                .contextualName("invoke_agent")
                .lowCardinalityKeyValue(AgentObservability.OPERATION, "invoke_agent")
                .lowCardinalityKeyValue(AgentObservability.AGENT_ID, AgentObservability.UNRESOLVED)
                .lowCardinalityKeyValue(AgentObservability.LLM_ID, AgentObservability.UNRESOLVED)
                .lowCardinalityKeyValue(AgentObservability.REQUEST_MODEL, AgentObservability.UNRESOLVED)
                .lowCardinalityKeyValue(AgentObservability.OUTCOME, "error")
                .highCardinalityKeyValue(AgentObservability.SESSION_ID, String.valueOf(sessionId));
        AgentObservability.attachStats(observation.getContext());
        var captured = content.prepare(userMessage);
        if (captured != null) {
            observation.highCardinalityKeyValue(AgentObservability.USER_MESSAGE, captured);
        }
        return observation.start();
    }

    /** The answer as the user reads it — on the span only when content is recorded. */
    private void tagResponse(Observation observation, String response) {
        var captured = content.prepare(response);
        if (captured != null) {
            observation.highCardinalityKeyValue(AgentObservability.AGENT_RESPONSE, captured);
        }
    }

    private static void tagAgent(Observation observation, AgentConfig config) {
        observation.contextualName("invoke_agent " + config.agentId())
                .lowCardinalityKeyValue(AgentObservability.AGENT_ID, config.agentId())
                .highCardinalityKeyValue(AgentObservability.AGENT_NAME, String.valueOf(config.agentName()));
        if (config.llm() != null) {
            observation.lowCardinalityKeyValue(AgentObservability.LLM_ID, String.valueOf(config.llm().id()))
                    .lowCardinalityKeyValue(AgentObservability.REQUEST_MODEL, String.valueOf(config.llm().model()));
        }
    }

    private static void tagTools(Observation observation, PerRequestMcpClientFactory.PerRequestTools tools,
                                 int toolCount) {
        observation.highCardinalityKeyValue(AgentObservability.TOOLS_AVAILABLE, String.valueOf(toolCount))
                .highCardinalityKeyValue(AgentObservability.MCP_SERVERS_EXPECTED, String.valueOf(tools.expectedServers()))
                .highCardinalityKeyValue(AgentObservability.MCP_SERVERS_CONNECTED, String.valueOf(tools.connectedServers()));
    }

    private static void outcome(Observation observation, String outcome) {
        observation.lowCardinalityKeyValue(AgentObservability.OUTCOME, outcome);
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
     * Every tool this agent has, in one array: the MCP servers' and the RAG sources'. The model
     * sees one list, and nothing downstream has to know which kind a call belongs to.
     */
    private org.springframework.ai.tool.ToolCallback[] allTools(
            AgentConfig config, PerRequestMcpClientFactory.PerRequestTools mcp) {
        var rag = ragTools.toolsFor(config.rags());
        if (rag.isEmpty()) {
            return mcp.getCallbacks();
        }
        var all = new ArrayList<org.springframework.ai.tool.ToolCallback>(
                List.of(mcp.getCallbacks()));
        all.addAll(rag);
        return all.toArray(new org.springframework.ai.tool.ToolCallback[0]);
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

    // ── Internal types ───────────────────────────────────────────────────────

    private record LlmResult(String content, int inputTokens, int outputTokens, int totalTokens) {}

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildSystemPrompt(String basePrompt, String serverContext, String sessionId) {
        var sb = new StringBuilder(basePrompt);
        if (serverContext != null && !serverContext.isBlank()) {
            sb.append("\n\nContexto de las herramientas disponibles:\n\n").append(serverContext);
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

            try (var tools = mcpFactory.createTools(config.mcpUrls(), authorization)) {
                var toolCallbacks = allTools(config, tools);
                tagTools(observation, tools, toolCallbacks.length);
                // Only a hard stop when there is nothing at all to call. An agent whose MCP
                // servers are all down but which still has a RAG source can answer from its
                // documents, and refusing here would take that away.
                if (tools.hasNoServers() && config.rags().isEmpty()) {
                    String err = "No hay ningún servidor MCP disponible (" + tools.expectedServers()
                            + " configurados, 0 conectados) ni ninguna fuente RAG. No puedo "
                            + "responder sin acceso a las herramientas.";
                    log.warn("Chat aborted session={}: {}", sessionId, err);
                    tagResponse(observation, err);
                    outcome(observation, "no_tools");
                    return err;
                }
                String systemPrompt = buildSystemPrompt(config.systemPrompt(),
                        tools.getServerSystemContext(), sessionId);
                var history = conversationStore.getHistory(sessionId);

                var chatResponse = chatClients.forLlm(config.llm()).prompt()
                        .options(chatClients.optionsFor(config.llm()))
                        .system(systemPrompt)
                        .messages(history)
                        .user(request.message())
                        .toolCallbacks(toolCallbacks)
                        .call()
                        .chatResponse();

                String content = null;
                int inputTokens = 0, outputTokens = 0, totalTokens = 0;
                if (chatResponse != null) {
                    var result = chatResponse.getResult();
                    if (result != null && result.getOutput() != null) {
                        content = result.getOutput().getText();
                    }
                    var usage = chatResponse.getMetadata() != null ? chatResponse.getMetadata().getUsage() : null;
                    if (usage != null) {
                        inputTokens  = usage.getPromptTokens()     != null ? usage.getPromptTokens()     : 0;
                        outputTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                        totalTokens  = usage.getTotalTokens()      != null ? usage.getTotalTokens()      : 0;
                    }
                }
                log.info("Chat response session={}: {} chars, tokens={}/{}/{}",
                        sessionId, content != null ? content.length() : 0,
                        inputTokens, outputTokens, totalTokens);

                String raw = (content != null && !content.isBlank()) ? content : "(sin respuesta)";
                String result = parseNavigation(raw).cleanText();
                conversationStore.addExchange(sessionId, request.message(), result);
                conversationStore.accumulateTokens(sessionId, inputTokens, outputTokens, totalTokens);
                usageReporter.report(config.agentId(), config.llm().id(), config.llm().model(),
                        inputTokens, outputTokens, totalTokens,
                        jwtIdentityReader.read(authorization), sessionId);
                tagResponse(observation, result);
                outcome(observation, "success");
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
        try (var tools = mcpFactory.createTools(config.mcpUrls(), authorization)) {
            var toolCallbacks = allTools(config, tools);
            tagTools(observation, tools, toolCallbacks.length);
            if (tools.hasNoServers() && config.rags().isEmpty()) {
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
                    tools.getServerSystemContext(), sessionId);
            var chatResponse = chatClients.forLlm(config.llm()).prompt()
                    .options(chatClients.optionsFor(config.llm()))
                    .system(systemPrompt)
                    .messages(history)
                    .user(request.message())
                    .toolCallbacks(toolCallbacks)
                    .call()
                    .chatResponse();

            String content = null;
            int inputTokens = 0, outputTokens = 0, totalTokens = 0;

            if (chatResponse != null) {
                var result = chatResponse.getResult();
                if (result != null && result.getOutput() != null) {
                    content = result.getOutput().getText();
                }
                var usage = chatResponse.getMetadata() != null
                        ? chatResponse.getMetadata().getUsage() : null;
                if (usage != null) {
                    inputTokens  = usage.getPromptTokens()     != null ? usage.getPromptTokens()     : 0;
                    outputTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                    totalTokens  = usage.getTotalTokens()      != null ? usage.getTotalTokens()      : 0;
                }
            }

            log.info("Stream completed session={}: {} chars, tokens={}/{}/{}",
                    sessionId, content != null ? content.length() : 0,
                    inputTokens, outputTokens, totalTokens);

            String raw = (content != null && !content.isBlank()) ? content : "(sin respuesta)";
            conversationStore.addExchange(sessionId, request.message(), raw);
            conversationStore.accumulateTokens(sessionId, inputTokens, outputTokens, totalTokens);
            usageReporter.report(config.agentId(), config.llm().id(), config.llm().model(),
                    inputTokens, outputTokens, totalTokens,
                    jwtIdentityReader.read(authorization), sessionId);
            tagResponse(observation, raw);
            outcome(observation, "success");
            int[] cumulative = conversationStore.getTotalTokens(sessionId);
            return new LlmResult(raw, cumulative[0], cumulative[1], cumulative[2]);
        }
    }
}
