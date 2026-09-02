package io.mateu.ecdemo1.iacp.infra.out.gitops;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.mateu.ecdemo1.iacp.application.out.gitops.CatalogueSource;
import io.mateu.ecdemo1.iacp.application.out.gitops.DesiredCatalogue;
import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.AgentManifest;
import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.ApiMcpManifest;
import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.BudgetManifest;
import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.LlmManifest;
import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.McpManifest;
import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.RagManifest;
import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.RouteManifest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

/**
 * Reads the desired catalogue from a directory in a GitHub repo, over the REST API with a token.
 *
 * <p>Two calls make a sync: one to the git-trees API to list every file under the configured path
 * in one recursive request, and one per YAML file for its raw content. The trees call is what keeps
 * the file count off the round-trip count — listing a hundred entries is one request, then one
 * fetch each, rather than walking directories.
 *
 * <p><strong>Any failure throws, and that is the contract the reconciler depends on.</strong> A
 * non-2xx from GitHub, a file that will not parse, a {@code kind} that is not one of the seven —
 * each aborts the whole fetch. The reason is deletion: the reconciler removes git-managed entries
 * absent from what this returns, so a fetch that quietly dropped a broken file would read as "that
 * entry was deleted from the repo" and remove it. Better to change nothing and log loudly until the
 * file is fixed.
 */
@Component
@ConditionalOnProperty(name = "cp.gitops.enabled", havingValue = "true")
@Slf4j
public class GitHubCatalogueSource implements CatalogueSource {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final YAMLMapper yaml = (YAMLMapper) new YAMLMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String repo;
    private final String branch;
    private final String path;
    private final String token;
    private final String apiBase;

    public GitHubCatalogueSource(
            @Value("${cp.gitops.repo:}") String repo,
            @Value("${cp.gitops.branch:main}") String branch,
            @Value("${cp.gitops.path:ia}") String path,
            @Value("${cp.gitops.token:}") String token,
            @Value("${cp.gitops.api-base:https://api.github.com}") String apiBase) {
        this.repo = repo;
        this.branch = branch;
        this.path = path.replaceAll("^/+|/+$", "");
        this.token = token;
        this.apiBase = apiBase.replaceAll("/+$", "");
        log.info("GitOps source: {} branch {} path {}/", repo, branch, this.path);
    }

    @Override
    public DesiredCatalogue fetch() {
        var llms = new ArrayList<LlmManifest>();
        var mcps = new ArrayList<McpManifest>();
        var apiMcps = new ArrayList<ApiMcpManifest>();
        var rags = new ArrayList<RagManifest>();
        var agents = new ArrayList<AgentManifest>();
        var budgets = new ArrayList<BudgetManifest>();
        var routes = new ArrayList<RouteManifest>();

        for (var file : listYamlFiles()) {
            var node = parse(file);
            var kind = node.hasNonNull("kind") ? node.get("kind").asText() : null;
            if (kind == null) {
                throw new IllegalStateException("File '" + file + "' has no 'kind' — cannot tell "
                        + "which catalogue it belongs to.");
            }
            switch (kind) {
                case "llm" -> llms.add(convert(node, LlmManifest.class, file));
                case "mcp" -> mcps.add(convert(node, McpManifest.class, file));
                // apimcp and not api-mcp: a kind is a word in a file somebody types,
                // and one spelling is one fewer thing to get wrong.
                case "apimcp" -> apiMcps.add(convert(node, ApiMcpManifest.class, file));
                case "rag" -> rags.add(convert(node, RagManifest.class, file));
                case "agent" -> agents.add(convert(node, AgentManifest.class, file));
                case "budget" -> budgets.add(convert(node, BudgetManifest.class, file));
                case "route" -> routes.add(convert(node, RouteManifest.class, file));
                default -> throw new IllegalStateException("File '" + file + "' has kind '" + kind
                        + "', which is not one of llm, mcp, apimcp, rag, agent, budget, "
                        + "route.");
            }
        }
        log.info("GitOps fetched {} llm, {} mcp, {} apimcp, {} rag, {} agent, {} budget, {} route "
                        + "from {}/{}", llms.size(), mcps.size(), apiMcps.size(), rags.size(),
                agents.size(), budgets.size(), routes.size(), repo, path);
        return new DesiredCatalogue(llms, mcps, apiMcps, rags, agents, budgets, routes);
    }

    /** Every {@code .yaml}/{@code .yml} blob under the configured path, via the git-trees API. */
    private List<String> listYamlFiles() {
        var uri = URI.create(apiBase + "/repos/" + repo + "/git/trees/"
                + URLEncoder.encode(branch, StandardCharsets.UTF_8) + "?recursive=1");
        var response = send(uri, "application/vnd.github+json", "list files");
        var files = new ArrayList<String>();
        try {
            var tree = yaml.readTree(response).get("tree");
            var prefix = path.isEmpty() ? "" : path + "/";
            if (tree != null) {
                for (var entry : tree) {
                    if (!"blob".equals(entry.path("type").asText())) {
                        continue;
                    }
                    var p = entry.path("path").asText();
                    if (p.startsWith(prefix) && (p.endsWith(".yaml") || p.endsWith(".yml"))) {
                        files.add(p);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the file listing from GitHub", e);
        }
        return files;
    }

    private JsonNode parse(String filePath) {
        var uri = URI.create(apiBase + "/repos/" + repo + "/contents/"
                + encodePath(filePath) + "?ref=" + URLEncoder.encode(branch, StandardCharsets.UTF_8));
        var body = send(uri, "application/vnd.github.raw", "read " + filePath);
        try {
            return yaml.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("File '" + filePath + "' is not valid YAML", e);
        }
    }

    private <T> T convert(JsonNode node, Class<T> type, String file) {
        try {
            return yaml.treeToValue(node, type);
        } catch (Exception e) {
            throw new IllegalStateException("File '" + file + "' does not match the "
                    + type.getSimpleName() + " shape: " + e.getMessage(), e);
        }
    }

    private String send(URI uri, String accept, String what) {
        try {
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                    .header("Accept", accept)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET();
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("GitHub refused to " + what + ": HTTP "
                        + response.statusCode() + " " + trim(response.body()));
            }
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("GitHub call failed (" + what + "): " + e, e);
        }
    }

    /** Path-encode each segment but keep the slashes — the contents API wants {@code a/b/c.yaml}. */
    private static String encodePath(String p) {
        var out = new StringBuilder();
        for (var seg : p.split("/")) {
            if (!out.isEmpty()) {
                out.append('/');
            }
            out.append(URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return out.toString();
    }

    private static String trim(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
