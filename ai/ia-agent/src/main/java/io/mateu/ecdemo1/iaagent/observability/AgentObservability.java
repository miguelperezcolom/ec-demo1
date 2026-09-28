package io.mateu.ecdemo1.iaagent.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationView;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What a prompt looks like in a trace and in the metrics, and the one piece of glue that makes
 * Spring AI's own observations say which agent they belong to.
 *
 * <p>Every prompt runs inside one {@value #PROMPT} observation — span {@code invoke_agent <agent>}
 * — opened by the controller. Spring AI's observations nest under it: {@code spring.ai.chat.client},
 * {@code gen_ai.client.operation} (one per round trip to the model) and {@code spring.ai.tool}
 * (one per tool call). Those know the model and the tool but not the agent, which is a concept of
 * this service, so {@link #agentIdPropagation()} copies {@value #AGENT_ID} down from the prompt
 * onto each of them before they are recorded. That is what puts the agent on
 * {@code gen_ai_client_operation_seconds}, {@code gen_ai_client_token_usage_total} and
 * {@code spring_ai_tool_seconds} as a label, not only on the spans.
 *
 * <p>{@link #promptSpans(ContentCapture)} does the rest on the way out of each observation: it
 * adds up the tokens of every model call and counts the tool calls into the prompt that caused
 * them, and — only when {@link ContentCapture} records content — writes the conversation onto the
 * span that saw it, in OpenTelemetry's GenAI attribute names.
 */
@Configuration(proxyBeanMethods = false)
public class AgentObservability {

    /** The observation around one prompt: metric {@code ia_agent_prompt_seconds}. */
    public static final String PROMPT = "ia.agent.prompt";

    /** OpenTelemetry's GenAI semantic-convention names, so Tempo shows the usual attributes. */
    public static final String AGENT_ID = "gen_ai.agent.id";
    public static final String AGENT_NAME = "gen_ai.agent.name";
    public static final String OPERATION = "gen_ai.operation.name";
    public static final String REQUEST_MODEL = "gen_ai.request.model";
    public static final String INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String OUTPUT_TOKENS = "gen_ai.usage.output_tokens";

    /** The catalogue's LLM id — the {@code llm} label of the control plane's ia_tokens_total. */
    public static final String LLM_ID = "ia.llm.id";
    /** success | refused | no_tools | error. */
    public static final String OUTCOME = "ia.outcome";
    public static final String SESSION_ID = "ia.session.id";
    public static final String TOOLS_AVAILABLE = "ia.tools.available";
    public static final String MCP_SERVERS_EXPECTED = "ia.mcp.servers.expected";
    public static final String MCP_SERVERS_CONNECTED = "ia.mcp.servers.connected";

    /** Totals over the whole prompt, on {@code invoke_agent}. */
    public static final String MODEL_CALLS = "ia.model.calls";
    public static final String TOOL_CALLS = "ia.tool.calls";
    public static final String TOOLS_CALLED = "ia.tools.called";
    /** Content on {@code invoke_agent}: what the user wrote and what the agent answered, as text. */
    public static final String USER_MESSAGE = "ia.user.message";
    public static final String AGENT_RESPONSE = "ia.agent.response";

    /** Content on {@code chat <model>}, in the GenAI semantic conventions' JSON shape. */
    public static final String SYSTEM_INSTRUCTIONS = "gen_ai.system_instructions";
    public static final String INPUT_MESSAGES = "gen_ai.input.messages";
    public static final String OUTPUT_MESSAGES = "gen_ai.output.messages";

    /** On {@code execute_tool <tool>}. */
    public static final String TOOL_NAME = "gen_ai.tool.name";
    public static final String TOOL_CALL_ID = "gen_ai.tool.call.id";
    public static final String TOOL_ARGUMENTS = "gen_ai.tool.call.arguments";
    public static final String TOOL_RESULT = "gen_ai.tool.call.result";
    /** success | error — the span's status says the same; this is searchable as an attribute. */
    public static final String TOOL_OUTCOME = "ia.tool.outcome";
    /** mcp | rag. */
    public static final String TOOL_SOURCE = "ia.tool.source";
    /** The MCP server the tool belongs to: its host ({@code booking}) and its URL. */
    public static final String MCP_SERVER = "ia.mcp.server";
    public static final String MCP_SERVER_URL = "ia.mcp.server.url";
    public static final String RAG_ID = "ia.rag.id";
    /** The agent an {@code ask_<peer>} tool called over A2A. */
    public static final String A2A_PEER = "ia.a2a.peer";
    /** How many A2A hops led to this prompt: 0 for a person's, 1 for an agent called by it... */
    public static final String A2A_DEPTH = "ia.a2a.depth";

    /** Before the control plane has said which agent this is — or when it refused to. */
    public static final String UNRESOLVED = "unresolved";

    /**
     * What one prompt added up to, filled in by its children as they stop and read when it does.
     * Kept in the prompt observation's context rather than returned by the ChatClient, because
     * that one answers with the usage of the last response only as far as the model reports it;
     * this counts every round trip and every tool call that actually happened under the prompt.
     */
    public static final class PromptStats {
        private final AtomicLong inputTokens = new AtomicLong();
        private final AtomicLong outputTokens = new AtomicLong();
        private final AtomicInteger modelCalls = new AtomicInteger();
        private final AtomicInteger toolCalls = new AtomicInteger();
        private final Set<String> toolsCalled = new ConcurrentSkipListSet<>();

        public long inputTokens() { return inputTokens.get(); }
        public long outputTokens() { return outputTokens.get(); }
        public int modelCalls() { return modelCalls.get(); }
        public int toolCalls() { return toolCalls.get(); }
        public Set<String> toolsCalled() { return Set.copyOf(toolsCalled); }
    }

    /** Called by the controller on the prompt observation before it starts. */
    public static PromptStats attachStats(Observation.Context prompt) {
        var stats = new PromptStats();
        prompt.put(PromptStats.class, stats);
        return stats;
    }

    @Bean
    ObservationFilter promptSpans(ContentCapture content) {
        return context -> {
            if (context instanceof ChatModelObservationContext chat) {
                onModelCall(chat, content);
            } else if (context instanceof ToolCallingObservationContext tool) {
                onToolCall(tool, content);
            } else if (PROMPT.equals(context.getName())) {
                onPrompt(context);
            }
            return context;
        };
    }

    private static void onPrompt(Observation.Context context) {
        PromptStats stats = context.get(PromptStats.class);
        if (stats == null) {
            return;
        }
        context.addHighCardinalityKeyValue(KeyValue.of(INPUT_TOKENS, String.valueOf(stats.inputTokens())));
        context.addHighCardinalityKeyValue(KeyValue.of(OUTPUT_TOKENS, String.valueOf(stats.outputTokens())));
        context.addHighCardinalityKeyValue(KeyValue.of(MODEL_CALLS, String.valueOf(stats.modelCalls())));
        context.addHighCardinalityKeyValue(KeyValue.of(TOOL_CALLS, String.valueOf(stats.toolCalls())));
        context.addHighCardinalityKeyValue(KeyValue.of(TOOLS_CALLED, String.join(",", stats.toolsCalled())));
    }

    private static void onModelCall(ChatModelObservationContext chat, ContentCapture content) {
        var stats = statsAbove(chat);
        var response = chat.getResponse();
        var usage = response != null && response.getMetadata() != null ? response.getMetadata().getUsage() : null;
        if (stats != null) {
            stats.modelCalls.incrementAndGet();
            if (usage != null) {
                stats.inputTokens.addAndGet(usage.getPromptTokens() != null ? usage.getPromptTokens() : 0);
                stats.outputTokens.addAndGet(usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0);
            }
        }
        if (!content.enabled()) {
            return;
        }
        var messages = GenAiMessages.of(chat.getRequest(), response);
        put(chat, SYSTEM_INSTRUCTIONS, content.prepare(messages.systemInstructions()));
        put(chat, INPUT_MESSAGES, content.prepare(messages.input()));
        put(chat, OUTPUT_MESSAGES, content.prepare(messages.output()));
    }

    private static void onToolCall(ToolCallingObservationContext tool, ContentCapture content) {
        String name = tool.getToolDefinition().name();
        var stats = statsAbove(tool);
        if (stats != null) {
            stats.toolCalls.incrementAndGet();
            stats.toolsCalled.add(name);
        }
        put(tool, OPERATION, "execute_tool");
        put(tool, TOOL_NAME, name);
        put(tool, TOOL_CALL_ID, tool.getToolCallId());
        put(tool, TOOL_OUTCOME, tool.getError() != null ? "error" : "success");
        if (content.enabled()) {
            put(tool, TOOL_ARGUMENTS, content.prepare(tool.getToolCallArguments()));
            put(tool, TOOL_RESULT, content.prepare(tool.getToolCallResult()));
        }
    }

    /** High cardinality: on the span only, never a metric label. Skipped when there is no value. */
    private static void put(Observation.Context context, String key, String value) {
        if (value != null) {
            context.addHighCardinalityKeyValue(KeyValue.of(key, value));
        }
    }

    private static PromptStats statsAbove(Observation.ContextView context) {
        ObservationView parent = context.getParentObservation();
        for (int depth = 0; parent != null && depth < 16; depth++) {
            var view = parent.getContextView();
            if (PROMPT.equals(view.getName())) {
                return view.get(PromptStats.class);
            }
            parent = view.getParentObservation();
        }
        return null;
    }

    @Bean
    ObservationFilter agentIdPropagation() {
        return context -> {
            var name = context.getName();
            if (name == null || !(name.startsWith("gen_ai.") || name.startsWith("spring.ai."))
                    || context.getLowCardinalityKeyValue(AGENT_ID) != null) {
                return context;
            }
            // Always set, "unresolved" when there is no prompt above it: a Prometheus meter must
            // carry the same label names on every sample, or the registry drops the odd one out.
            return context.addLowCardinalityKeyValue(KeyValue.of(AGENT_ID, agentIdAbove(context)));
        };
    }

    private static String agentIdAbove(Observation.ContextView context) {
        ObservationView parent = context.getParentObservation();
        // Bounded: advisor → chat client → prompt is three levels; the limit only guards a cycle.
        for (int depth = 0; parent != null && depth < 16; depth++) {
            var view = parent.getContextView();
            if (PROMPT.equals(view.getName())) {
                var agent = view.getLowCardinalityKeyValue(AGENT_ID);
                return agent != null ? agent.getValue() : UNRESOLVED;
            }
            parent = view.getParentObservation();
        }
        return UNRESOLVED;
    }
}
