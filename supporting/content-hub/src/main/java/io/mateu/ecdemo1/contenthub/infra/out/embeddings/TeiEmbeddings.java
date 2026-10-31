package io.mateu.ecdemo1.contenthub.infra.out.embeddings;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.contenthub.application.out.Embeddings;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The cluster's Text Embeddings Inference (deploy/manifests/12-embeddings.yaml), by its OpenAI-shaped
 * {@code /v1/embeddings}. It serves multilingual-e5-small, which wants "query: " before a question and
 * "passage: " before a passage.
 */
@Component
public class TeiEmbeddings implements Embeddings {

    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${content-hub.embeddings.url:http://embeddings}")
    String baseUrl;

    @Value("${content-hub.embeddings.model:intfloat/multilingual-e5-small}")
    String model;

    @Override
    public List<float[]> embed(List<String> texts, boolean query) {
        var input = texts.stream().map(t -> (query ? "query: " : "passage: ") + t).toList();
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(Map.of("model", model, "input", input))))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Embeddings answered " + response.statusCode() + ": " + response.body());
            }
            var data = Json.MAPPER.readTree(response.body()).path("data");
            var out = new ArrayList<float[]>();
            for (JsonNode item : data) {
                var e = item.path("embedding");
                var v = new float[e.size()];
                for (int i = 0; i < v.length; i++) {
                    v[i] = (float) e.get(i).asDouble();
                }
                out.add(v);
            }
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Embeddings unreachable: " + e.getMessage(), e);
        }
    }
}
