package io.mateu.ecdemo1.contenthub.infra.out.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.contenthub.application.out.HumanTasks;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The forms engine's task inbox over REST ({@code /forms/api/tasks}), with the person's own bearer
 * token: the engine trusts it (WORKFLOW_SECURITY_TRUST_FORWARDED_TOKEN), so what they see and complete
 * is decided there — their assigned tasks and the ones they are a candidate for.
 */
@Component
public class FormsHumanTasks implements HumanTasks {

    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final String baseUrl;

    public FormsHumanTasks(@Value("${content-hub.forms-url:http://forms:8106}") String baseUrl) {
        this.baseUrl = baseUrl.replaceFirst("/$", "");
    }

    @Override
    public List<Task> open(String bearer, List<String> formIds) {
        var out = new ArrayList<Task>();
        for (var formId : formIds) {
            var json = call("GET", "/forms/api/tasks?scope=all&formId=" + URLEncoder.encode(formId, StandardCharsets.UTF_8)
                    + "&sort=created&size=200", bearer, null);
            JsonNode items = Json.read(json, JsonNode.class).path("items");
            items.forEach(t -> out.add(new Task(t.path("id").asText(), t.path("formId").asText(formId),
                    t.path("businessKey").asText(null), t.path("status").asText(), t.path("assignee").asText(null),
                    t.path("dueDate").asText(null))));
        }
        return out;
    }

    @Override
    public void complete(String bearer, Task task, Map<String, Object> values) {
        if (!"ASSIGNED".equals(task.status())) {
            call("POST", "/forms/api/tasks/" + task.id() + "/claim", bearer, "{}");
        }
        call("POST", "/forms/api/tasks/" + task.id() + "/complete", bearer, Json.write(Map.of("values", values)));
    }

    String call(String method, String path, String bearer, String body) {
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json");
            if (bearer != null) {
                request.header("Authorization", bearer.startsWith("Bearer ") ? bearer : "Bearer " + bearer);
            }
            if (body != null) {
                request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(switch (response.statusCode()) {
                    case 403, 404 -> "Esa tarea no es tuya";
                    case 409 -> "Otra persona ya la tiene o ya está decidida";
                    case 400 -> "El motor no acepta la respuesta: " + response.body();
                    default -> "El motor de tareas respondió " + response.statusCode();
                });
            }
            return response.body();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se puede hablar con el motor de tareas: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
