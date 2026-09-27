package io.mateu.ecdemo1.iaagent.config;

import java.util.List;

/**
 * One agent's configuration, as the control plane resolved it.
 *
 * <p>The shape mirrors {@code ResolveAgentConfigUseCase.Resolved} on the other side. It is
 * restated here rather than shared through a jar on purpose: this is a wire contract between two
 * independently deployed services, and a shared class would make every field change a lockstep
 * release of both. Unknown fields are ignored on the way in, so the control plane can add one
 * without this service noticing.
 *
 * <p>{@code llm.apiKey} arrives in the clear. It is the reason the endpoint that serves this has
 * no gateway route.
 */
public record AgentConfig(
        String agentId,
        String agentName,
        String systemPrompt,
        Llm llm,
        List<Mcp> mcps,
        List<Rag> rags,
        List<String> warnings) {

    public record Llm(String id, String name, String provider, String model, String baseUrl,
                      Double temperature, Integer maxTokens, String apiKey) {
    }

    public record Mcp(String id, String name, String url, String transport, long timeoutSeconds) {
    }

    /** {@code description} becomes the tool description the model reads. See RagToolFactory. */
    public record Rag(String id, String name, String kind, String connectionUrl, String collection,
                      String embeddingModel, int topK, String description) {
    }

    public List<String> mcpUrls() {
        return mcps == null ? List.of() : mcps.stream().map(Mcp::url).toList();
    }
}
