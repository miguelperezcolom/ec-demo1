package io.mateu.ecdemo1.iaagent.a2a;

import io.mateu.ecdemo1.iaagent.AgentTurn;
import io.mateu.ecdemo1.iaagent.ConversationStore;
import io.mateu.ecdemo1.iaagent.PerRequestMcpClientFactory;
import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.mateu.ecdemo1.iaagent.config.RagToolFactory;
import io.mateu.ecdemo1.iaagent.identity.JwtIdentityReader;
import io.mateu.ecdemo1.iaagent.observability.PromptObservations;
import io.mateu.ecdemo1.iaagent.usage.UsageReporter;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Answers an A2A message as the agent it is addressed to: the same tools, the same model and the
 * same {@link AgentTurn} as the chat panel, with three differences.
 *
 * <ul>
 *   <li>The conversation is the A2A {@code contextId}'s, not a browser session's — a caller that
 *       sends the same context back continues where it left off.</li>
 *   <li>Its peer tools are offered one hop deeper, and not at all past the depth limit or when the
 *       call must not delegate (see {@link PeerToolFactory}).</li>
 *   <li>No MCP server and no RAG source is not a refusal: an agent reached over A2A may well be one
 *       that only reasons — a classifier, a guardrail — and has no tools by design.</li>
 * </ul>
 *
 * <p>Usage is reported for the agent that answered, with the caller's identity from the forwarded
 * token, so a delegated prompt counts against the same person's budgets and shows up under the
 * agent that spent it. Those budgets have already been consulted when this runs: see
 * {@link A2aController}.
 *
 * <p><strong>Guardrails run where the call comes in from outside the agents</strong>, as they do
 * around the chat panel: on a call with nothing on its chain. An agent's call to a peer is inside
 * that perimeter, and a guardrail is what guards it, so running them there as well would not add a
 * check — and past the first hop would fail, the guardrail's own call being over the depth limit. A
 * refusal is {@link A2aServer.Blocked}: an error to the caller, never text it could take for the
 * agent's work. A blocked answer has been paid for, so its usage is reported all the same.
 */
@Component
public class A2aAgentExecutor implements A2aServer.Executor {

    private static final Logger log = LoggerFactory.getLogger(A2aAgentExecutor.class);

    /** The chat panel's navigation markers mean nothing to another agent. */
    private static final Pattern NAVIGATE = Pattern.compile("\\[NAVIGATE:\\{[^]]*}]", Pattern.DOTALL);

    private static final String A2A_NOTE = "Te está llamando otro agente por A2A, no una persona. "
            + "Responde en texto, de forma completa y sin marcadores de navegación: tu respuesta es "
            + "todo lo que el otro agente va a ver.";

    private final PerRequestMcpClientFactory mcpFactory;
    private final RagToolFactory ragTools;
    private final PeerToolFactory peerTools;
    private final AgentTurn agentTurn;
    private final ConversationStore conversationStore;
    private final UsageReporter usageReporter;
    private final JwtIdentityReader jwtIdentityReader;
    private final PromptObservations prompts;
    private final ObservationRegistry observationRegistry;
    private final GuardrailRunner guardrails;

    public A2aAgentExecutor(PerRequestMcpClientFactory mcpFactory, RagToolFactory ragTools,
                            PeerToolFactory peerTools, AgentTurn agentTurn,
                            ConversationStore conversationStore, UsageReporter usageReporter,
                            JwtIdentityReader jwtIdentityReader, PromptObservations prompts,
                            ObservationRegistry observationRegistry, GuardrailRunner guardrails) {
        this.guardrails = guardrails;
        this.mcpFactory = mcpFactory;
        this.ragTools = ragTools;
        this.peerTools = peerTools;
        this.agentTurn = agentTurn;
        this.conversationStore = conversationStore;
        this.usageReporter = usageReporter;
        this.jwtIdentityReader = jwtIdentityReader;
        this.prompts = prompts;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public String run(AgentConfig config, String text, String contextId, String authorization, A2aHop hop) {
        return run(config, text, List.of(), contextId, authorization, hop);
    }

    /** The images go to the model with this turn's message only: the conversation keeps the text. */
    @Override
    public String run(AgentConfig config, String text, List<A2aServer.Image> images, String contextId,
                      String authorization, A2aHop hop) {
        var media = images.stream().map(i -> Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(i.mimeType())).data(i.bytes()).build()).toList();
        var sessionId = "a2a:" + config.agentId() + ":" + contextId;
        log.info("A2A message for {} (depth {}, chain {}): {} chars, {} images", config.agentId(), hop.depth(),
                hop.chain(), text.length(), images.size());
        var observation = prompts.start(observationRegistry.getCurrentObservation(), sessionId, text,
                hop.depth());
        try (var scope = observation.openScope()) {
            PromptObservations.tagAgent(observation, config);
            var guarded = hop.chain().isEmpty() && !hop.noDelegation();
            var message = guarded ? checked(GuardrailRunner.Side.INPUT, observation, config, text, authorization, hop)
                    : text;
            try (var mcp = mcpFactory.createTools(config.mcpUrls(), authorization)) {
                var peers = peerTools.toolsFor(config, authorization, hop);
                var tools = new ArrayList<ToolCallback>(List.of(mcp.getCallbacks()));
                tools.addAll(ragTools.toolsFor(config.rags()));
                tools.addAll(peers);
                PromptObservations.tagTools(observation, tools.size(), mcp.expectedServers(),
                        mcp.connectedServers());

                var system = new StringBuilder(config.systemPrompt() == null ? "" : config.systemPrompt());
                var serverContext = mcp.getServerSystemContext();
                if (serverContext != null && !serverContext.isBlank()) {
                    system.append("\n\nContexto de las herramientas disponibles:\n\n").append(serverContext);
                }
                var peerContext = PeerToolFactory.systemContext(peers);
                if (!peerContext.isBlank()) {
                    system.append("\n\n").append(peerContext);
                }
                system.append("\n\n").append(A2A_NOTE);

                var history = conversationStore.getHistory(sessionId);
                var toolArray = tools.toArray(new ToolCallback[0]);
                var turn = media.isEmpty()
                        ? agentTurn.call(config, system.toString(), history, message, toolArray)
                        : agentTurn.call(config, system.toString(), history, message, media, toolArray);
                var answer = turn.content() == null || turn.content().isBlank()
                        ? "(sin respuesta)" : NAVIGATE.matcher(turn.content()).replaceAll("").trim();
                usageReporter.report(config.agentId(), config.llm().id(), config.llm().model(),
                        turn.inputTokens(), turn.outputTokens(), turn.totalTokens(),
                        jwtIdentityReader.read(authorization), sessionId);
                var content = guarded
                        ? checked(GuardrailRunner.Side.OUTPUT, observation, config, answer, authorization, hop)
                        : answer;
                conversationStore.addExchange(sessionId, message, content);
                log.info("A2A answer from {}: {} chars, tokens={}/{}/{}", config.agentId(), content.length(),
                        turn.inputTokens(), turn.outputTokens(), turn.totalTokens());
                prompts.tagResponse(observation, content);
                PromptObservations.outcome(observation, "success");
                return content;
            }
        } catch (A2aServer.Blocked e) {
            throw e; // a decision, already recorded on the observation: not a failure
        } catch (RuntimeException e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    /** The text the route's guardrails leave on this side, or the refusal if they stop it. */
    private String checked(GuardrailRunner.Side side, Observation observation, AgentConfig config, String text,
                           String authorization, A2aHop hop) {
        var outcome = guardrails.check(side, config, text, authorization, hop);
        if (!outcome.blocked()) {
            return outcome.text();
        }
        var refusal = GuardrailRunner.refusal(side, outcome);
        prompts.tagResponse(observation, refusal);
        PromptObservations.outcome(observation,
                side == GuardrailRunner.Side.INPUT ? "blocked_input" : "blocked_output");
        throw new A2aServer.Blocked(refusal);
    }
}
