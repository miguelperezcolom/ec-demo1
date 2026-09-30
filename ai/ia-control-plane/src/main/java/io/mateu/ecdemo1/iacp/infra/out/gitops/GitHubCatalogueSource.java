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
 * Reads the desired catalogue from a directory in a GitHub repo: the branch's tarball, in one call.
 *
 * <p>One request makes a sync, whatever the number of files — the tarball API answers with a
 * redirect to the archive, which is followed and read in memory. It used to be the git-trees API
 * plus one contents call per YAML file, and a public repo read without a token (60 API calls an hour
 * per IP, and the cluster's egress IP is shared) ran out at startup: GitHub answered 403 and the
 * catalogue was not reconciled. A token is still sent when there is one, which is what a private
 * repo needs; a public one needs none.
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

    // The tarball API answers 302 to codeload.github.com: followed.
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
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

        for (var entry : yamlFiles().entrySet()) {
            var file = entry.getKey();
            var node = parse(file, entry.getValue());
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

    /** Every {@code .yaml}/{@code .yml} file under the configured path, by path, from the branch's tarball. */
    private java.util.SortedMap<String, String> yamlFiles() {
        var uri = URI.create(apiBase + "/repos/" + repo + "/tarball/"
                + URLEncoder.encode(branch, StandardCharsets.UTF_8));
        var archive = sendBytes(uri, "download the " + branch + " tarball");
        try {
            return yamlFilesIn(archive, path);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read the tarball of " + repo + "@" + branch, e);
        }
    }

    /**
     * The YAML files under {@code path} in a GitHub tarball (gzipped tar), keyed by their path in the
     * repo. GitHub puts everything under one top directory ({@code owner-repo-sha/}), which is
     * dropped; pax and GNU long-name headers are honoured, since a long path is written with one.
     */
    static java.util.SortedMap<String, String> yamlFilesIn(byte[] tarGz, String path) throws java.io.IOException {
        var prefix = path.isEmpty() ? "" : path + "/";
        var files = new java.util.TreeMap<String, String>();
        try (var in = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(tarGz)))) {
            var header = new byte[512];
            String longName = null;
            while (true) {
                try {
                    in.readFully(header);
                } catch (java.io.EOFException e) {
                    break;
                }
                if (header[0] == 0) {
                    break; // the two zero blocks that end the archive
                }
                var name = field(header, 0, 100);
                var prefixField = field(header, 345, 155);
                var size = Long.parseLong(field(header, 124, 12).trim().isEmpty() ? "0" : field(header, 124, 12).trim(), 8);
                var type = (char) header[156];
                var data = new byte[(int) size];
                in.readFully(data);
                in.skipNBytes((512 - size % 512) % 512);
                if (type == 'L') {                       // GNU long name: the next entry's
                    longName = new String(data, StandardCharsets.UTF_8).replace("\0", "");
                    continue;
                }
                if (type == 'x') {                       // pax: a path=… record overrides the name
                    for (var line : new String(data, StandardCharsets.UTF_8).split("\n")) {
                        var at = line.indexOf(" path=");
                        if (at >= 0) {
                            longName = line.substring(at + 6);
                        }
                    }
                    continue;
                }
                if (type == 'g') {                       // pax global header (GitHub: the commit)
                    continue;
                }
                var full = longName != null ? longName : prefixField.isEmpty() ? name : prefixField + "/" + name;
                longName = null;
                if (type != '0' && type != 0) {
                    continue;                            // directories, links
                }
                var slash = full.indexOf('/');
                var inRepo = slash < 0 ? full : full.substring(slash + 1);
                if (inRepo.startsWith(prefix) && (inRepo.endsWith(".yaml") || inRepo.endsWith(".yml"))) {
                    files.put(inRepo, new String(data, StandardCharsets.UTF_8));
                }
            }
        }
        return files;
    }

    private static String field(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private JsonNode parse(String filePath, String body) {
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

    private byte[] sendBytes(URI uri, String what) {
        try {
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET();
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("GitHub refused to " + what + ": HTTP "
                        + response.statusCode() + " " + trim(new String(response.body(), StandardCharsets.UTF_8)));
            }
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("GitHub call failed (" + what + "): " + e, e);
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
