package io.mateu.ecdemo1.iaagent.observability;

import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

/**
 * The {@code invoke_agent} span and the {@code ia_agent_prompt_seconds} timer: one per prompt,
 * parent of every model round trip and tool call it causes — whether the prompt came from the chat
 * panel or from another agent over A2A. {@code parent} is passed in rather than taken from the
 * thread because /stream answers on another one than the request's.
 *
 * <p>Every low-cardinality key is set at the start, with a placeholder, and overwritten as it
 * becomes known: they are Prometheus labels, and a meter must carry the same ones on every sample.
 *
 * <p>The tokens, model calls and tool calls it ends with are not set here: every model round trip
 * and tool call under it adds itself up in {@link AgentObservability.PromptStats} as it stops, and
 * {@link AgentObservability} writes the totals when this one does. The user's message goes on it
 * only when {@link ContentCapture} records content.
 */
@Component
public class PromptObservations {

    private final ObservationRegistry observationRegistry;
    private final ContentCapture content;

    public PromptObservations(ObservationRegistry observationRegistry, ContentCapture content) {
        this.observationRegistry = observationRegistry;
        this.content = content;
    }

    public Observation start(Observation parent, String sessionId, String userMessage, int a2aDepth) {
        var observation = Observation.createNotStarted(AgentObservability.PROMPT, observationRegistry)
                .parentObservation(parent)
                .contextualName("invoke_agent")
                .lowCardinalityKeyValue(AgentObservability.OPERATION, "invoke_agent")
                .lowCardinalityKeyValue(AgentObservability.AGENT_ID, AgentObservability.UNRESOLVED)
                .lowCardinalityKeyValue(AgentObservability.LLM_ID, AgentObservability.UNRESOLVED)
                .lowCardinalityKeyValue(AgentObservability.REQUEST_MODEL, AgentObservability.UNRESOLVED)
                .lowCardinalityKeyValue(AgentObservability.OUTCOME, "error")
                .highCardinalityKeyValue(AgentObservability.SESSION_ID, String.valueOf(sessionId))
                .highCardinalityKeyValue(AgentObservability.A2A_DEPTH, String.valueOf(a2aDepth));
        AgentObservability.attachStats(observation.getContext());
        var captured = content.prepare(userMessage);
        if (captured != null) {
            observation.highCardinalityKeyValue(AgentObservability.USER_MESSAGE, captured);
        }
        return observation.start();
    }

    /** The answer as the user reads it — on the span only when content is recorded. */
    public void tagResponse(Observation observation, String response) {
        var captured = content.prepare(response);
        if (captured != null) {
            observation.highCardinalityKeyValue(AgentObservability.AGENT_RESPONSE, captured);
        }
    }

    public static void tagAgent(Observation observation, AgentConfig config) {
        observation.contextualName("invoke_agent " + config.agentId())
                .lowCardinalityKeyValue(AgentObservability.AGENT_ID, config.agentId())
                .highCardinalityKeyValue(AgentObservability.AGENT_NAME, String.valueOf(config.agentName()));
        if (config.llm() != null) {
            observation.lowCardinalityKeyValue(AgentObservability.LLM_ID, String.valueOf(config.llm().id()))
                    .lowCardinalityKeyValue(AgentObservability.REQUEST_MODEL, String.valueOf(config.llm().model()));
        }
    }

    public static void tagTools(Observation observation, int toolCount, int expectedServers,
                                int connectedServers) {
        observation.highCardinalityKeyValue(AgentObservability.TOOLS_AVAILABLE, String.valueOf(toolCount))
                .highCardinalityKeyValue(AgentObservability.MCP_SERVERS_EXPECTED, String.valueOf(expectedServers))
                .highCardinalityKeyValue(AgentObservability.MCP_SERVERS_CONNECTED, String.valueOf(connectedServers));
    }

    public static void outcome(Observation observation, String outcome) {
        observation.lowCardinalityKeyValue(AgentObservability.OUTCOME, outcome);
    }
}
