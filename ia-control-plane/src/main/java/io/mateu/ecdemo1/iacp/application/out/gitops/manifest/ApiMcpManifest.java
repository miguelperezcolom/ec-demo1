package io.mateu.ecdemo1.iacp.application.out.gitops.manifest;

import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiKind;

import java.util.List;

/**
 * One API-offered-as-MCP entry as the repo declares it.
 *
 * <p><b>{@code apiKind} and not {@code kind}</b>, which reads oddly until you look at a file: every
 * manifest here carries a top-level {@code kind} naming which catalogue it belongs to, and this is
 * the only catalogue whose entries also have a kind of their own. Two fields called {@code kind} in
 * one document would bind the discriminator into the entry and fail every file.
 *
 * <p>The tools are the entry — an API-backed server's offer is composed rather than declared by the
 * server, which is the whole reason this is a separate catalogue from {@code mcp}. Two rules make
 * the declarative form safe:
 *
 * <ul>
 *   <li><b>Absent {@code tools} leaves the offer alone.</b> A file that simply does not mention
 *       them is asking for nothing in particular, and wiping a composed offer because somebody
 *       edited the base url in a hurry is not what they asked for. This is the same convention
 *       {@code credentialEnv} already follows.</li>
 *   <li><b>An explicit empty list exposes nothing</b>, which is how the repo says so on purpose.
 *       The entry stays catalogued and stops being usable, visibly.</li>
 * </ul>
 *
 * <p>{@code credentialEnv} names an environment variable, never a secret. The key stays in a Secret
 * on this deployment and the repo holds only which one to read — see the LLM manifest, which
 * established the rule.
 */
public record ApiMcpManifest(String id, String name, ApiKind apiKind, String baseUrl,
                             String specUrl, String description, Boolean enabled,
                             String credentialEnv, List<ToolManifest> tools) {

    /**
     * One operation offered as a tool.
     *
     * <p>{@code description} is the load-bearing field, and it is load-bearing in a repo for the
     * same reason it is on the screen: it is what a model reads to decide whether to call the
     * thing. An operationId pasted from a spec is not a description.
     */
    public record ToolManifest(String operation, String toolName, String description,
                               List<String> requiredRoles) {
    }
}
