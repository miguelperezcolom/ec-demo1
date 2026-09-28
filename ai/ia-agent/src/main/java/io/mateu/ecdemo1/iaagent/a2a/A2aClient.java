package io.mateu.ecdemo1.iaagent.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iaagent.observability.TraceHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends one text message to an A2A agent and returns its text answer: {@code message/send} over
 * JSON-RPC 2.0, the A2A protocol's blocking call.
 *
 * <p>Written against the protocol rather than this deployment's server, so it reads either answer
 * the protocol allows — a {@code Message}, which is what {@link A2aServer} returns, or a
 * {@code Task}, whose text is in its artifacts or its status message. What it does not do is
 * stream, poll a task left running, or register push notifications: an agent that can only answer
 * that way is reported as not having answered.
 *
 * <p>The caller's bearer token goes along unchanged, so the agent at the other end acts for the
 * same person — reads their identity, reaches its MCP servers with their token, charges their
 * budget. The trace headers go along too, so the other agent's prompt is part of this one's trace.
 */
@Component
public class A2aClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final TraceHeaders traceHeaders;
    private final Duration timeout;

    public A2aClient(TraceHeaders traceHeaders,
                     @Value("${ia.a2a.timeout-seconds:120}") long timeoutSeconds) {
        this.traceHeaders = traceHeaders;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /** What came back: the text, and the context to continue in. */
    public record Reply(String text, String contextId) {
    }

    /** Anything that is not an answer, as a sentence. */
    public static class A2aException extends RuntimeException {
        public A2aException(String message) { super(message); }
    }

    /**
     * @param hop the hop the <em>receiving</em> agent is at — already {@link A2aHop#next}-ed.
     * @param contextId null to start a new conversation with that agent.
     */
    public Reply send(String url, String text, String authorization, A2aHop hop, String contextId) {
        HttpResponse<String> response;
        try {
            var builder = traceHeaders.applyTo(HttpRequest.newBuilder(URI.create(url)))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header(A2aHop.DEPTH_HEADER, String.valueOf(hop.depth()))
                    .header(A2aHop.CHAIN_HEADER, hop.chainHeader());
            if (hop.noDelegation()) {
                builder.header(A2aHop.NO_DELEGATION_HEADER, "true");
            }
            if (authorization != null && !authorization.isBlank()) {
                builder.header("Authorization", authorization);
            }
            response = http.send(builder.POST(HttpRequest.BodyPublishers.ofString(
                    mapper.writeValueAsString(request(text, contextId)))).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new A2aException("no answer within " + timeout.toSeconds() + " s");
        } catch (Exception e) {
            throw new A2aException("unreachable (" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")");
        }
        if (response.statusCode() != 200) {
            throw new A2aException("HTTP " + response.statusCode()
                    + (response.body() == null || response.body().isBlank() ? "" : ": " + abbreviate(response.body())));
        }
        return read(response.body());
    }

    static Map<String, Object> request(String text, String contextId) {
        var message = new LinkedHashMap<String, Object>();
        message.put("kind", "message");
        message.put("role", "user");
        message.put("messageId", UUID.randomUUID().toString());
        message.put("parts", List.of(Map.of("kind", "text", "text", text)));
        if (contextId != null) {
            message.put("contextId", contextId);
        }
        var params = new LinkedHashMap<String, Object>();
        params.put("message", message);
        params.put("configuration", Map.of("blocking", true, "acceptedOutputModes", List.of("text/plain")));
        var request = new LinkedHashMap<String, Object>();
        request.put("jsonrpc", "2.0");
        request.put("id", UUID.randomUUID().toString());
        request.put("method", "message/send");
        request.put("params", params);
        return request;
    }

    /** A JSON-RPC response to message/send, whichever of the two shapes its result takes. */
    Reply read(String body) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception e) {
            throw new A2aException("answered something that is not JSON: " + abbreviate(body));
        }
        if (root.hasNonNull("error")) {
            var error = root.get("error");
            throw new A2aException(error.path("message").asText("error") + " (code "
                    + error.path("code").asText("?") + ")");
        }
        var result = root.path("result");
        var contextId = result.path("contextId").asText(null);
        var kind = result.path("kind").asText("");
        if ("task".equals(kind)) {
            var state = result.path("status").path("state").asText("");
            var text = new StringBuilder();
            result.path("artifacts").forEach(a -> text.append(texts(a.path("parts"))));
            if (text.isEmpty()) {
                text.append(texts(result.path("status").path("message").path("parts")));
            }
            if (List.of("failed", "rejected", "canceled").contains(state)) {
                throw new A2aException("the task ended " + state
                        + (text.isEmpty() ? "" : ": " + text));
            }
            if (!"completed".equals(state) && text.isEmpty()) {
                throw new A2aException("the task was left " + state + " with no answer yet; "
                        + "this client does not poll");
            }
            return new Reply(text.toString(), contextId);
        }
        var text = texts(result.path("parts"));
        if (text.isEmpty()) {
            throw new A2aException("answered with no text");
        }
        return new Reply(text, contextId);
    }

    private static String texts(JsonNode parts) {
        var sb = new StringBuilder();
        if (parts.isArray()) {
            for (var part : parts) {
                // "kind" is the current spec's discriminator; a text part with none is still text.
                if (part.hasNonNull("text") && ("text".equals(part.path("kind").asText("text")))) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(part.get("text").asText());
                }
            }
        }
        return sb.toString();
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
