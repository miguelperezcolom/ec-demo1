package io.mateu.ecdemo1.apimcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.apimcp.call.ApiCaller;
import io.mateu.ecdemo1.apimcp.catalogue.CataloguedApi;
import io.mateu.ecdemo1.apimcp.catalogue.CatalogueClient;
import io.mateu.ecdemo1.apimcp.spec.ResolvedSpec;
import io.mateu.ecdemo1.apimcp.spec.SpecResolver;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live set of MCP endpoints, one per catalogued API.
 *
 * <p>Rebuilt from the catalogue on a schedule rather than pushed to, because the alternative is
 * the control plane knowing this pod exists — and the whole point of the split is that it does
 * not. A poll is late by at most one interval, which for "an operator changed a tool description"
 * is the right trade against a second service to notify and a second thing to get wrong.
 *
 * <p>Only entries that actually CHANGED are rebuilt; the signature covers everything a mounted
 * server is built from. Recreating an unchanged endpoint would drop every connected agent's
 * session on every poll.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiMcpServers {

    private final CatalogueClient catalogue;
    private final SpecResolver specs;
    private final ApiCaller caller;
    private final ObjectMapper json = new ObjectMapper();

    private final Map<String, Mounted> mounted = new ConcurrentHashMap<>();

    /** One catalogue entry, served. The signature is what it was built from. */
    private record Mounted(WebMvcSseServerTransportProvider provider, McpSyncServer server,
                           int signature, int toolCount) {
    }

    /** The endpoints as request routes, read at REQUEST time so the set can change under them. */
    public List<RouterFunction<ServerResponse>> routerFunctions() {
        return mounted.values().stream().map(m -> m.provider().getRouterFunction()).toList();
    }

    public int endpointCount() {
        return mounted.size();
    }

    /**
     * How many times an endpoint has been built since this pod started.
     *
     * <p>Not the same as the endpoint count, and the difference is the thing worth watching: a
     * number that climbs on every poll means the signature is not recognising an unchanged entry,
     * and every connected agent is losing its session each time.
     */
    private int mountsPerformed;

    int mountsPerformed() {
        return mountsPerformed;
    }

    /**
     * Reads the catalogue and makes the mounted endpoints match it.
     *
     * <p>Runs on startup too — the first poll is what mounts anything at all. A control plane that
     * is not up yet leaves this pod running with no endpoints and saying so through its readiness
     * probe, which is the honest state and not a crash loop.
     */
    @Scheduled(initialDelayString = "${api-mcp.refresh.initial-delay:2s}",
               fixedDelayString = "${api-mcp.refresh.interval:30s}")
    public void refresh() {
        List<CataloguedApi> apis;
        try {
            apis = catalogue.fetch();
        } catch (RuntimeException e) {
            log.warn("The catalogue could not be read and nothing is mounted yet: {}",
                    e.getMessage());
            return;
        }
        var wanted = apis.stream().map(CataloguedApi::id).toList();
        for (var id : List.copyOf(mounted.keySet())) {
            if (!wanted.contains(id)) {
                unmount(id, "it is no longer in the catalogue");
            }
        }
        for (var api : apis) {
            var signature = signatureOf(api);
            var current = mounted.get(api.id());
            if (current != null && current.signature() == signature) {
                continue;
            }
            if (current != null) {
                // The spec may be why it changed, and a cached document would mount the old
                // shapes under the new offer.
                specs.forget(api.specUrl());
                unmount(api.id(), "it changed");
            }
            mount(api, signature);
        }
    }

    private void mount(CataloguedApi api, int signature) {
        if (!"REST".equals(api.kind())) {
            // Catalogued and honestly not served: the control plane allows a SOAP entry to exist
            // before anything can read a WSDL, and mounting an endpoint that answers with no
            // tools would look like a working server offering nothing.
            log.warn("API {} is {} and is not served — only REST is implemented", api.id(),
                    api.kind());
            return;
        }
        ResolvedSpec spec;
        try {
            spec = specs.resolve(api.specUrl());
        } catch (RuntimeException e) {
            log.error("API {} is not served: its spec could not be read — {}", api.id(),
                    e.getMessage());
            return;
        }

        var tools = new ArrayList<McpServerFeatures.SyncToolSpecification>();
        for (var tool : api.tools()) {
            var operation = spec.operation(tool.operation()).orElse(null);
            if (operation == null) {
                // The spec changed under an offer composed against an older one. Dropped rather
                // than mounted, because a tool naming an operation that no longer exists fails at
                // call time, in front of a person, instead of here in a log.
                log.error("API {} exposes {} as '{}', which its spec no longer declares — dropped",
                        api.id(), tool.operation(), tool.toolName());
                continue;
            }
            if (tool.requiredRoles() != null && !tool.requiredRoles().isEmpty()) {
                // Fails CLOSED, and this is the one place in this service worth stopping at.
                // The catalogue records required roles and says they are checked where the call
                // is made — which is here. This transport hands a tool handler no HTTP request,
                // so there is nothing to read a caller's roles from, and offering the tool
                // anyway would turn a stated restriction into no restriction at all. Until this
                // pod can see the caller, a narrowed tool is not offered.
                log.warn("API {} exposes '{}' to roles {} — not served: this service cannot see "
                        + "the caller yet, and offering it would ignore that restriction",
                        api.id(), tool.toolName(), tool.requiredRoles());
                continue;
            }
            tools.add(specificationFor(api, spec, operation, tool));
        }

        if (tools.isEmpty()) {
            log.warn("API {} mounts nothing: none of its {} exposed tool(s) survived",
                    api.id(), api.tools().size());
            return;
        }

        var provider = new WebMvcSseServerTransportProvider(json,
                "/" + api.id() + "/message", "/" + api.id() + "/sse");
        var server = McpServer.sync(provider)
                .serverInfo(api.id(), "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .instructions(instructionsFor(api))
                .tools(tools)
                .build();
        mounted.put(api.id(), new Mounted(provider, server, signature, tools.size()));
        mountsPerformed++;
        log.info("API {} is served at /{}/sse with {} tool(s)", api.id(), api.id(), tools.size());
    }

    private McpServerFeatures.SyncToolSpecification specificationFor(
            CataloguedApi api, ResolvedSpec spec, ResolvedSpec.Operation operation,
            CataloguedApi.Tool tool) {
        var descriptor = new McpSchema.Tool(tool.toolName(), tool.description(),
                schemaJson(operation));
        return new McpServerFeatures.SyncToolSpecification(descriptor, (exchange, arguments) -> {
            var outcome = caller.call(api.baseUrl(), api.secret(), spec.credential(), operation,
                    arguments);
            return new McpSchema.CallToolResult(outcome.text(), !outcome.ok());
        });
    }

    private String schemaJson(ResolvedSpec.Operation operation) {
        try {
            return json.writeValueAsString(operation.inputSchema());
        } catch (Exception e) {
            // An object with no properties: the tool is still callable for an operation that
            // takes nothing, and unusable rather than wrong for one that does.
            log.error("The input schema for {} {} could not be written",
                    operation.method(), operation.pathTemplate(), e);
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }

    /**
     * What the model is told about the API as a whole, above the individual tools.
     *
     * <p>The catalogue's description, which is written for whoever reads the catalogue. It is the
     * only prose here that was not written for a model, and it is included anyway because an API's
     * purpose is context no per-tool description repeats.
     */
    private String instructionsFor(CataloguedApi api) {
        return api.description() == null || api.description().isBlank()
                ? api.name()
                : api.name() + " — " + api.description();
    }

    private void unmount(String id, String why) {
        var gone = mounted.remove(id);
        if (gone == null) {
            return;
        }
        try {
            gone.server().closeGracefully();
        } catch (RuntimeException e) {
            log.warn("The MCP server for {} did not close cleanly: {}", id, e.getMessage());
        }
        log.info("API {} is no longer served — {}", id, why);
    }

    /**
     * Everything a mounted server is built from, and nothing else.
     *
     * <p>The secret is in it so a rotated key remounts, and it is a hash so nothing derived from
     * it can be logged. The description is in it because it becomes the server's instructions.
     */
    private static int signatureOf(CataloguedApi api) {
        return Objects.hash(api.kind(), api.baseUrl(), api.specUrl(), api.secret(),
                api.description(), api.tools());
    }

    @PreDestroy
    void closeAll() {
        List.copyOf(mounted.keySet()).forEach(id -> unmount(id, "this pod is shutting down"));
    }
}
