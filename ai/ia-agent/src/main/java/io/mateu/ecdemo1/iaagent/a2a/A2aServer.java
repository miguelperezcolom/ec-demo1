package io.mateu.ecdemo1.iaagent.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.mateu.ecdemo1.iaagent.config.AgentConfigClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The A2A protocol as this service speaks it: an Agent Card per agent, and JSON-RPC 2.0
 * {@code message/send}. Plain Java — no Spring, no model — so the protocol's shape is testable on
 * its own; {@link A2aController} puts it on HTTP and {@link A2aAgentExecutor} answers with the
 * agent.
 *
 * <p><strong>The subset, deliberately.</strong> Implemented: the card at
 * {@code /a2a/{agentId}/.well-known/agent-card.json}, and {@code message/send} with text parts,
 * answered with a {@code Message} — which the specification allows for a call that completes at
 * once, and which is what an agent that answers in one turn does. Not implemented, and said so in
 * the card and in the errors: streaming ({@code message/stream}, {@code tasks/resubscribe} →
 * UnsupportedOperation), push notifications ({@code tasks/pushNotificationConfig/*} →
 * PushNotificationNotSupported), tasks ({@code tasks/get}, {@code tasks/cancel} → TaskNotFound,
 * since none is ever kept), and file or data parts (ContentTypeNotSupported). Field names follow the
 * A2A specification 0.3.0: {@code kind} discriminators, {@code messageId}, {@code contextId},
 * {@code parts}.
 *
 * <p>Two refusals are this deployment's own and use the JSON-RPC server-error range: a call past
 * the maximum A2A depth, and a call that would close a cycle ({@link A2aHop}).
 */
public class A2aServer {

    private static final Logger log = LoggerFactory.getLogger(A2aServer.class);

    public static final String PROTOCOL_VERSION = "0.3.0";

    // JSON-RPC 2.0
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    // A2A
    public static final int TASK_NOT_FOUND = -32001;
    public static final int PUSH_NOTIFICATION_NOT_SUPPORTED = -32003;
    public static final int UNSUPPORTED_OPERATION = -32004;
    public static final int CONTENT_TYPE_NOT_SUPPORTED = -32005;
    // This deployment's, in the server-error range
    public static final int DEPTH_EXCEEDED = -32050;
    public static final int CYCLE = -32051;
    public static final int AGENT_UNAVAILABLE = -32052;

    private static final Set<String> STREAMING = Set.of("message/stream", "tasks/resubscribe");

    /** An agent's configuration by id. Throws {@link AgentConfigClient.Unavailable} with the reason. */
    public interface Configs {
        AgentConfig configOf(String agentId);
    }

    /** Runs one turn of an agent and returns its text answer. */
    public interface Executor {
        String run(AgentConfig config, String text, String contextId, String authorization, A2aHop hop);
    }

    private final Configs configs;
    private final Executor executor;
    private final int maxDepth;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public A2aServer(Configs configs, Executor executor, int maxDepth, String baseUrl) {
        this.configs = configs;
        this.executor = executor;
        this.maxDepth = maxDepth;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String urlOf(String agentId) {
        return baseUrl + "/a2a/" + agentId;
    }

    /** The Agent Card, or null when the catalogue has no servable agent by that id. */
    public Map<String, Object> agentCard(String agentId) {
        AgentConfig config;
        try {
            config = configs.configOf(agentId);
        } catch (AgentConfigClient.Unavailable e) {
            return null;
        }
        var description = config.agentDescription() == null || config.agentDescription().isBlank()
                ? String.valueOf(config.agentName()) : config.agentDescription();
        var card = new LinkedHashMap<String, Object>();
        card.put("protocolVersion", PROTOCOL_VERSION);
        card.put("name", config.agentName());
        card.put("description", description);
        card.put("url", urlOf(agentId));
        card.put("preferredTransport", "JSONRPC");
        card.put("version", "1.0.0");
        card.put("capabilities", Map.of(
                "streaming", false,
                "pushNotifications", false,
                "stateTransitionHistory", false));
        card.put("securitySchemes", Map.of("bearer", Map.of(
                "type", "http", "scheme", "bearer", "bearerFormat", "JWT",
                "description", "The caller's own token; the agent acts for that person.")));
        card.put("security", List.of(Map.of("bearer", List.of())));
        card.put("defaultInputModes", List.of("text/plain"));
        card.put("defaultOutputModes", List.of("text/plain"));
        card.put("skills", List.of(Map.of(
                "id", agentId,
                "name", String.valueOf(config.agentName()),
                "description", description,
                "tags", List.of("ec-demo1"))));
        return card;
    }

    /** One JSON-RPC request in, one JSON-RPC response out. Never throws. */
    public Map<String, Object> handle(String agentId, String body, String authorization, A2aHop hop) {
        JsonNode request;
        try {
            request = mapper.readTree(body);
        } catch (Exception e) {
            return error(null, PARSE_ERROR, "Parse error");
        }
        if (request == null || !request.isObject()) {
            return error(null, INVALID_REQUEST, "Invalid Request");
        }
        Object id = request.has("id") ? mapper.convertValue(request.get("id"), Object.class) : null;
        if (!"2.0".equals(request.path("jsonrpc").asText()) || !request.path("method").isTextual()) {
            return error(id, INVALID_REQUEST, "Invalid Request: jsonrpc must be \"2.0\" and method a string");
        }
        var method = request.get("method").asText();
        if (STREAMING.contains(method)) {
            return error(id, UNSUPPORTED_OPERATION, "This agent does not stream; use message/send");
        }
        if (method.startsWith("tasks/pushNotificationConfig/")) {
            return error(id, PUSH_NOTIFICATION_NOT_SUPPORTED, "Push Notification is not supported");
        }
        if ("tasks/get".equals(method) || "tasks/cancel".equals(method)) {
            return error(id, TASK_NOT_FOUND, "Task not found: this agent answers with messages and keeps no tasks");
        }
        if (!"message/send".equals(method)) {
            return error(id, METHOD_NOT_FOUND, "Method not found: " + method);
        }
        return messageSend(id, agentId, request.path("params"), authorization, hop);
    }

    private Map<String, Object> messageSend(Object id, String agentId, JsonNode params, String authorization,
                                            A2aHop hop) {
        var message = params.path("message");
        if (!message.isObject() || !message.path("parts").isArray()) {
            return error(id, INVALID_PARAMS, "Invalid params: params.message.parts is required");
        }
        var text = new StringBuilder();
        boolean otherParts = false;
        for (var part : message.path("parts")) {
            if ("text".equals(part.path("kind").asText("text")) && part.hasNonNull("text")) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(part.get("text").asText());
            } else {
                otherParts = true;
            }
        }
        if (text.isEmpty()) {
            return otherParts
                    ? error(id, CONTENT_TYPE_NOT_SUPPORTED, "Incompatible content types: only text parts are accepted")
                    : error(id, INVALID_PARAMS, "Invalid params: the message has no text");
        }
        if (hop.depth() > maxDepth) {
            log.warn("A2A call to {} refused: depth {} is past the limit of {} (chain {})", agentId,
                    hop.depth(), maxDepth, hop.chain());
            return error(id, DEPTH_EXCEEDED, "A2A depth limit exceeded: this call is hop " + hop.depth()
                    + " and the limit is " + maxDepth);
        }
        if (hop.chain().contains(agentId)) {
            log.warn("A2A call to {} refused: it is already on the chain {}", agentId, hop.chain());
            return error(id, CYCLE, "A2A cycle refused: agent '" + agentId + "' is already on this chain "
                    + hop.chain());
        }
        AgentConfig config;
        try {
            config = configs.configOf(agentId);
        } catch (AgentConfigClient.Unavailable e) {
            return error(id, AGENT_UNAVAILABLE, "Agent '" + agentId + "' is not available: " + e.getMessage());
        }
        var contextId = message.path("contextId").asText(null);
        if (contextId == null || contextId.isBlank()) {
            contextId = UUID.randomUUID().toString();
        }
        String answer;
        try {
            answer = executor.run(config, text.toString(), contextId, authorization, hop);
        } catch (Exception e) {
            log.error("A2A call to {} failed: {}", agentId, e.toString(), e);
            return error(id, INTERNAL_ERROR, "Internal error: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
        var reply = new LinkedHashMap<String, Object>();
        reply.put("kind", "message");
        reply.put("messageId", UUID.randomUUID().toString());
        reply.put("role", "agent");
        reply.put("parts", List.of(Map.of("kind", "text", "text", answer)));
        reply.put("contextId", contextId);
        return result(id, reply);
    }

    static Map<String, Object> result(Object id, Object result) {
        var response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    static Map<String, Object> error(Object id, int code, String message) {
        var response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }
}
