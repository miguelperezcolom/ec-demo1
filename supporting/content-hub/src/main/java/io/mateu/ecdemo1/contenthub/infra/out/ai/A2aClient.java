package io.mateu.ecdemo1.contenthub.infra.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
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
 * One text message to an agent of the IA control plane, over A2A ({@code message/send}, JSON-RPC 2.0,
 * blocking): the plane's LLM, usage and audit apply, and content-hub holds no model key.
 *
 * <p>The token is the person's who asked, never a service's: the agent acts for them and they are charged.
 * Each call is a new conversation (no {@code contextId}), so one reader's text never joins another's history,
 * and it may not delegate: these agents only read what they are sent.
 */
@Component
public class A2aClient {

    /** Anything that is not an answer. Its message is for the log, not for the user. */
    public static class A2aException extends RuntimeException {
        public A2aException(String message) {
            super(message);
        }
    }

    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    /** @return the agent's text answer, never blank */
    public String send(String url, String text, String authorization, Duration timeout) {
        if (url == null || url.isBlank()) {
            throw new A2aException("no agent configured");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new A2aException("no bearer token of the user");
        }
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", authorization)
                    .header("X-A2A-No-Delegation", "true")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(request(text)))).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new A2aException("no answer within " + timeout.toSeconds() + " s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new A2aException("interrupted");
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new A2aException("unreachable (" + e.getClass().getSimpleName() + ")");
        }
        if (response.statusCode() != 200) {
            throw new A2aException("HTTP " + response.statusCode());
        }
        return read(response.body());
    }

    static Map<String, Object> request(String text) {
        var message = new LinkedHashMap<String, Object>();
        message.put("kind", "message");
        message.put("role", "user");
        message.put("messageId", UUID.randomUUID().toString());
        message.put("parts", List.of(Map.of("kind", "text", "text", text)));
        var request = new LinkedHashMap<String, Object>();
        request.put("jsonrpc", "2.0");
        request.put("id", UUID.randomUUID().toString());
        request.put("method", "message/send");
        request.put("params", Map.of("message", message,
                "configuration", Map.of("blocking", true, "acceptedOutputModes", List.of("text/plain"))));
        return request;
    }

    /** A JSON-RPC response: a Message (what ia-agent answers) or a finished Task (what the protocol also allows). */
    static String read(String body) {
        JsonNode root;
        try {
            root = Json.MAPPER.readTree(body);
        } catch (java.io.IOException e) {
            throw new A2aException("answered something that is not JSON");
        }
        if (root.hasNonNull("error")) {
            var error = root.get("error");
            throw new A2aException(error.path("message").asText("error") + " (code " + error.path("code").asText("?") + ")");
        }
        var result = root.path("result");
        String text;
        if ("task".equals(result.path("kind").asText())) {
            if (!"completed".equals(result.path("status").path("state").asText())) {
                throw new A2aException("the task was left " + result.path("status").path("state").asText());
            }
            var sb = new StringBuilder();
            result.path("artifacts").forEach(a -> sb.append(texts(a.path("parts"))));
            text = sb.isEmpty() ? texts(result.path("status").path("message").path("parts")) : sb.toString();
        } else {
            text = texts(result.path("parts"));
        }
        // "(sin respuesta)" is how ia-agent says the model answered nothing
        if (text.isBlank() || "(sin respuesta)".equals(text.trim())) {
            throw new A2aException("answered with no text");
        }
        return text;
    }

    static String texts(JsonNode parts) {
        var sb = new StringBuilder();
        for (var part : parts) {
            if (part.hasNonNull("text") && "text".equals(part.path("kind").asText("text"))) {
                sb.append(sb.isEmpty() ? "" : "\n").append(part.get("text").asText());
            }
        }
        return sb.toString();
    }
}
