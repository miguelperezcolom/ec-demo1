package io.mateu.ecdemo1.iaagent.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 * <p>Nothing here records content. The prompt, the completion, a tool's arguments and its result
 * are customer data; they stay out unless the {@code spring.ai.*.observations} switches in
 * application.yaml are turned on.
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

    /** Before the control plane has said which agent this is — or when it refused to. */
    public static final String UNRESOLVED = "unresolved";

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
