package io.mateu.ecdemo1.apimcp.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One catalogued API as this service reads it.
 *
 * <p><b>A restatement of the control plane's shape, not a shared jar</b> — the same decision the
 * agent's {@code AgentConfig} makes, for the same reason: these are two independently deployed
 * services and a shared DTO would couple their release cycles to no benefit. Unknown properties
 * are ignored, so the control plane can add a field without this pod refusing to start.
 *
 * <p>{@code secret} arrives in the clear. It never leaves this process except as whatever header
 * or query parameter the API's own spec says it belongs in, and it is never logged — see
 * {@link #toString()}, which exists solely to make that true of a record.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CataloguedApi(String id, String name, String kind, String baseUrl, String specUrl,
                            String secret, List<Tool> tools, String description) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tool(String operation, String toolName, String description,
                       List<String> requiredRoles) {
    }

    public List<Tool> tools() {
        return tools == null ? List.of() : tools;
    }

    public boolean hasSecret() {
        return secret != null && !secret.isBlank();
    }

    @Override
    public String toString() {
        return "CataloguedApi[" + id + " " + kind + " " + tools().size() + " tools, credential "
                + (hasSecret() ? "set" : "unset") + "]";
    }
}
