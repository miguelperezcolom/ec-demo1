package io.mateu.ecdemo1.iacp.infra.out.ragstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iacp.application.out.rag.RagStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The OpenAI embeddings API, spoken directly.
 *
 * <p>This exists instead of {@code spring-ai-openai} for a blunt reason: that artifact is built
 * against Spring Framework 6 and calls {@code HttpHeaders.addAll(MultiValueMap)}, which is gone in
 * Framework 7 — so on this service's Boot 4 it fails at the first request with a
 * {@code NoSuchMethodError} rather than at build time. The alternatives were to move this whole
 * module back to Boot 3.4, or to write the one POST that was actually being used.
 *
 * <p>One POST is what it is. {@code /v1/embeddings} takes a model and a list of strings and returns
 * a list of vectors; there is no streaming, no tool calling and no conversation. Dropping the
 * dependency also drops a chat client, a moderation client and an image client this service has no
 * use for.
 *
 * <p>Compatible endpoints — Ollama, vLLM, a gateway — speak the same shape at a different address,
 * which is what {@code baseUrl} on a catalogued LLM is for.
 */
class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String url;
    private final String apiKey;
    private final String model;

    OpenAiCompatibleEmbeddingModel(RagStore.EmbeddingSpec spec) {
        var base = spec.baseUrl() == null || spec.baseUrl().isBlank()
                ? DEFAULT_BASE_URL : spec.baseUrl().replaceAll("/+$", "");
        // Accept both a bare host and one that already names the version, because both are what
        // people put in a configuration field and neither is wrong.
        this.url = base.endsWith("/v1") ? base + "/embeddings" : base + "/v1/embeddings";
        this.apiKey = spec.apiKey();
        this.model = spec.model();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var inputs = request.getInstructions();
        if (inputs == null || inputs.isEmpty()) {
            return new EmbeddingResponse(List.of());
        }
        try {
            var body = mapper.writeValueAsString(Map.of("model", model, "input", inputs));
            var response = http.send(HttpRequest.newBuilder(URI.create(url))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // The body carries the provider's own explanation — an unknown model, a bad key,
                // a rate limit — and that sentence is worth more than the status code alone.
                throw new RagStore.UnsupportedStoreException("The embedding model answered "
                        + response.statusCode() + ": " + truncate(response.body()));
            }
            var data = mapper.readTree(response.body()).path("data");
            var embeddings = new ArrayList<Embedding>(inputs.size());
            for (int i = 0; i < data.size(); i++) {
                var vector = data.get(i).path("embedding");
                var floats = new float[vector.size()];
                for (int j = 0; j < vector.size(); j++) {
                    floats[j] = (float) vector.get(j).asDouble();
                }
                embeddings.add(new Embedding(floats, i));
            }
            if (embeddings.size() != inputs.size()) {
                // Silently short would mean documents stored against the wrong vectors.
                throw new RagStore.UnsupportedStoreException("The embedding model returned "
                        + embeddings.size() + " vectors for " + inputs.size() + " inputs.");
            }
            return new EmbeddingResponse(embeddings);
        } catch (RagStore.UnsupportedStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new RagStore.UnsupportedStoreException("Could not reach the embedding model at "
                    + url + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getFormattedContent());
    }

    private static String truncate(String s) {
        if (s == null) {
            return "(no body)";
        }
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
