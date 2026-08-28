package io.mateu.ecdemo1.iaagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Turns each RAG source in this agent's configuration into a tool the model can call.
 *
 * <p><strong>A tool, and not a prompt stuffed with context.</strong> Classic retrieval embeds the
 * user's question before the model sees it and pastes the results into the system prompt, which
 * means retrieving on every turn whether or not the question has anything to do with the
 * documents, and paying for those tokens every time. As a tool, the model decides — and this
 * agent's instructions already say to answer only by calling tools and to report a tool failure
 * rather than answering around it, so a source that is empty or unreachable produces a sentence
 * saying so instead of an invented answer.
 *
 * <p>It also composes: an MCP tool and a RAG tool are both {@link ToolCallback}s, so the LLM sees
 * one list and nothing in the request path has to know which is which.
 *
 * <p>The search itself happens in the control plane. This is a client — it sends a question and
 * gets passages back, and never holds the store's connection or the embedding credential.
 */
@Component
public class RagToolFactory {

    private static final Logger log = LoggerFactory.getLogger(RagToolFactory.class);

    /**
     * Longer than the MCP connect timeout: a query has to be embedded by a remote model before
     * anything is searched, and that round trip is the slow part.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "What to look for, in natural language. A question works better than a keyword."
                }
              },
              "required": ["query"]
            }
            """;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String controlPlaneUrl;

    public RagToolFactory(@Value("${ia.control-plane.url:http://localhost:8110}") String controlPlaneUrl) {
        this.controlPlaneUrl = controlPlaneUrl.replaceAll("/+$", "");
    }

    public List<ToolCallback> toolsFor(List<AgentConfig.Rag> rags) {
        if (rags == null || rags.isEmpty()) {
            return List.of();
        }
        return rags.stream().map(this::toolFor).toList();
    }

    private ToolCallback toolFor(AgentConfig.Rag rag) {
        var name = "search_" + rag.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        // The catalogue's own words first, because whoever wrote them knows what is in there;
        // the sentence after is what the model needs whatever the source is.
        var description = "Searches " + rag.name() + ". "
                + (rag.description() == null || rag.description().isBlank()
                        ? "" : rag.description().trim() + " ")
                + "Returns the passages that best match a question. Use it when the answer might "
                + "be written down in this source rather than obtainable from another tool.";
        var definition = DefaultToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(INPUT_SCHEMA)
                .build();
        return new RagToolCallback(definition, rag);
    }

    /**
     * One tool. It returns text rather than a structure on purpose: everything it produces goes
     * into a prompt, and a failure has to read as a sentence the model can repeat rather than a
     * status code it has to interpret.
     */
    private class RagToolCallback implements ToolCallback {

        private final ToolDefinition definition;
        private final AgentConfig.Rag rag;

        RagToolCallback(ToolDefinition definition, AgentConfig.Rag rag) {
            this.definition = definition;
            this.rag = rag;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            String query;
            try {
                var node = mapper.readTree(toolInput);
                query = node.path("query").asText(null);
            } catch (Exception e) {
                return "The search could not be run: the tool input was not valid JSON.";
            }
            if (query == null || query.isBlank()) {
                return "The search could not be run: no query was given.";
            }
            try {
                var body = mapper.writeValueAsString(
                        java.util.Map.of("query", query, "topK", rag.topK()));
                var response = http.send(HttpRequest
                                .newBuilder(URI.create(controlPlaneUrl + "/internal/rag/"
                                        + rag.id() + "/search"))
                                .timeout(TIMEOUT)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    var reason = mapper.readTree(response.body()).path("reason").asText(response.body());
                    log.warn("RAG search on {} refused: {}", rag.id(), reason);
                    return "The search against " + rag.name() + " did not run: " + reason;
                }
                var passages = mapper.readTree(response.body()).path("passages");
                if (!passages.isArray() || passages.isEmpty()) {
                    // Said plainly, because "nothing found" and "this is broken" must not read
                    // the same to a model that has been told to report failures.
                    return "The search against " + rag.name()
                            + " ran and found nothing matching: " + query;
                }
                var sb = new StringBuilder("Passages from " + rag.name() + ":\n");
                passages.forEach(p -> sb.append("---\n").append(p.path("text").asText()).append('\n'));
                return sb.toString();
            } catch (Exception e) {
                var message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("RAG search on {} failed: {}", rag.id(), message);
                return "The search against " + rag.name() + " could not be reached: " + message;
            }
        }
    }
}
