package io.mateu.ecdemo1.iaagent;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Creates a fresh set of McpSyncClient connections for every prompt.
 *
 * Why: Spring AI's auto-configured McpSyncClient holds a persistent SSE connection.
 * When that connection breaks the client is permanently broken and all subsequent
 * tool calls fail.  Opening a new connection per request avoids this: each prompt
 * gets a healthy transport, and the transport is discarded immediately afterwards.
 *
 * System prompt: after connecting to each server this factory reads the MCP Prompt
 * named "system-context" (if the server exposes it) and returns the combined text
 * via {@link PerRequestTools#getServerSystemContext()}.  The controller appends this
 * to the local base prompt to build the final system message for the LLM.
 *
 * Blocking issue: AnthropicChatModel invokes tool callbacks on the Netty event-loop
 * thread. Reactor forbids .block() there.  SyncMcpToolCallbackProvider.call()
 * internally calls McpSyncClient.callTool().block().
 * Fix: submit the actual call to a dedicated thread pool and wait with plain
 * Future.get() — no blocking check applies to plain Java futures.
 */
@Component
public class PerRequestMcpClientFactory {

    private static final Logger log = LoggerFactory.getLogger(PerRequestMcpClientFactory.class);
    private static final String SYSTEM_CONTEXT_PROMPT = "system-context";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final ExecutorService executor;

    public PerRequestMcpClientFactory() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mcp-tool-executor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Opens a fresh connection to every configured MCP server, initialises the MCP
     * session, collects tool callbacks and the "system-context" prompt from each
     * server.  Callers MUST call {@link PerRequestTools#close()} when the prompt
     * finishes (use try-with-resources).
     *
     * <p>The server list is a parameter rather than a field because it is no longer this pod's to
     * know: it comes from the control plane with every prompt, so enabling a server in the console
     * takes effect on the next one without a restart.
     *
     * @param serverUrls          the MCP servers this agent is configured with, right now
     * @param authorizationHeader value of the incoming Authorization header (may be
     *                            null or blank); when present it is forwarded to every
     *                            MCP server so they can enforce their own authorization.
     */
    public PerRequestTools createTools(List<String> serverUrls, String authorizationHeader) {
        // Connect to all MCP servers in parallel so total wait = max(individual timeouts)
        // instead of sum(individual timeouts).
        List<CompletableFuture<McpConnection>> futures = serverUrls.stream()
                .map(url -> CompletableFuture.supplyAsync(
                        () -> connectToServer(url, authorizationHeader), executor))
                .toList();

        long timeoutSecs = CONNECT_TIMEOUT.toSeconds() + 5;
        List<McpSyncClient> clients = new ArrayList<>();
        List<String> serverContexts = new ArrayList<>();

        for (CompletableFuture<McpConnection> future : futures) {
            try {
                McpConnection conn = future.get(timeoutSecs, TimeUnit.SECONDS);
                if (conn != null) {
                    clients.add(conn.client());
                    if (conn.systemContext() != null) {
                        serverContexts.add(conn.systemContext());
                    }
                }
            } catch (Exception e) {
                log.warn("MCP connection timed out or failed: {}", e.getMessage());
            }
        }

        ToolCallback[] rawCallbacks = new SyncMcpToolCallbackProvider(clients).getToolCallbacks();
        ToolCallback[] wrapped = wrapWithExecutor(dropDuplicateNames(rawCallbacks));
        log.info("Per-request MCP tools ready: {} tools from {}/{} servers",
                wrapped.length, clients.size(), serverUrls.size());
        return new PerRequestTools(clients, wrapped, serverContexts, serverUrls.size());
    }

    private record McpConnection(McpSyncClient client, String systemContext) {}

    private McpConnection connectToServer(String url, String authorizationHeader) {
        try {
            var transportBuilder = HttpClientSseClientTransport.builder(url)
                    .customizeClient(cb -> cb.connectTimeout(CONNECT_TIMEOUT));
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                // The customizer sees every request the transport makes — the SSE stream and each
                // message POST — which is what the caller's token has to reach for a server that
                // enforces its own authorization.
                transportBuilder.httpRequestCustomizer(
                        (requestBuilder, method, uri, body, context) ->
                                requestBuilder.header("Authorization", authorizationHeader));
            }
            McpSyncClient client = McpClient.sync(transportBuilder.build())
                    .requestTimeout(REQUEST_TIMEOUT)
                    .clientInfo(new McpSchema.Implementation(clientNameFor(url), "1.0"))
                    .build();
            client.initialize();
            log.debug("MCP client connected: {}", url);
            return new McpConnection(client, readSystemContext(client, url));
        } catch (Exception e) {
            log.warn("Could not connect to MCP server {} — skipping: {}", url, e.getMessage());
            return null;
        }
    }

    private String readSystemContext(McpSyncClient client, String url) {
        try {
            boolean hasPrompt = client.listPrompts().prompts().stream()
                    .anyMatch(p -> SYSTEM_CONTEXT_PROMPT.equals(p.name()));
            if (!hasPrompt) {
                return null;
            }
            var result = client.getPrompt(
                    new McpSchema.GetPromptRequest(SYSTEM_CONTEXT_PROMPT, Map.of()));
            String text = result.messages().stream()
                    .filter(m -> m.content() instanceof McpSchema.TextContent)
                    .map(m -> ((McpSchema.TextContent) m.content()).text())
                    .findFirst()
                    .orElse(null);
            if (text != null) {
                log.debug("System context from {}: {} chars", url, text.length());
            }
            return text;
        } catch (Exception e) {
            log.warn("Could not read system-context prompt from {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * A name per server, and the reason is not cosmetic.
     *
     * <p>Spring AI builds every tool's name as {@code <client name>_<tool name>}, taken from the
     * CLIENT's info — see {@code SyncMcpToolCallback.getToolDefinition} — not from the server's.
     * One shared client name for every connection therefore erases the only thing that
     * distinguishes two servers, and two of them exposing a tool of the same name produce two
     * functions with one name in the request. Anthropic tolerated that; a Vertex model behind an
     * OpenAI-compatible gateway rejects the whole call with a 400 that names neither the tool nor
     * the reason, so every prompt fails and nothing says why.
     *
     * <p>The host is what actually distinguishes these servers — {@code http://booking:8108}
     * becomes {@code booking} — so the tool the model sees is {@code booking_findBookings} rather
     * than {@code ia_agent_service_findBookings}, which is also the more useful name.
     */
    static String clientNameFor(String url) {
        String host = null;
        try {
            host = URI.create(url).getHost();
        } catch (Exception e) {
            // A URL this malformed will fail at connection time with a better message than
            // anything this method could produce. Fall through to the raw string.
        }
        var basis = (host == null || host.isBlank()) ? url : host;
        var sanitised = basis.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return sanitised.isBlank() ? "mcp" : sanitised;
    }

    /**
     * Last line of defence for the same failure: two servers behind the same host, or a provider
     * stricter still. One tool missing is a degraded answer; a duplicate function name is every
     * prompt failing, so the first one wins and the collision is reported rather than hidden.
     */
    private ToolCallback[] dropDuplicateNames(ToolCallback[] callbacks) {
        var byName = new LinkedHashMap<String, ToolCallback>();
        for (ToolCallback callback : callbacks) {
            var name = callback.getToolDefinition().name();
            if (byName.putIfAbsent(name, callback) != null) {
                log.warn("Two MCP servers expose a tool named '{}'. Keeping the first and dropping"
                        + " the second — a duplicate function name is refused outright by some"
                        + " providers, which costs every prompt rather than one tool.", name);
            }
        }
        return byName.values().toArray(ToolCallback[]::new);
    }

    private ToolCallback[] wrapWithExecutor(ToolCallback[] callbacks) {
        return Arrays.stream(callbacks)
                .map(cb -> (ToolCallback) new ToolCallback() {
                    @Override
                    public ToolDefinition getToolDefinition() {
                        return cb.getToolDefinition();
                    }

                    @Override
                    public String call(String toolInput) {
                        String toolName = cb.getToolDefinition().name();
                        log.info("MCP tool call: {} input={}", toolName, toolInput);
                        try {
                            String result = executor.submit(() -> cb.call(toolInput))
                                    .get(60, TimeUnit.SECONDS);
                            log.info("MCP tool result: {} -> {}", toolName, result);
                            return result;
                        } catch (ExecutionException e) {
                            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                            log.error("MCP tool {} execution error: {}", toolName, msg);
                            return "{\"error\":true,\"tool\":\"" + toolName + "\","
                                    + "\"message\":\"HERRAMIENTA NO DISPONIBLE: " + toolName
                                    + " falló con el error: " + msg + ". "
                                    + "NO inventes datos. Informa al usuario de este error.\"}";
                        } catch (Exception e) {
                            log.error("MCP tool {} call failed: {}", toolName, e.getMessage());
                            return "{\"error\":true,\"tool\":\"" + toolName + "\","
                                    + "\"message\":\"HERRAMIENTA NO DISPONIBLE: " + toolName
                                    + " no respondió a tiempo o no está levantada. "
                                    + "NO inventes datos. Informa al usuario de este error.\"}";
                        }
                    }
                })
                .toArray(ToolCallback[]::new);
    }

    /** Holds per-request MCP clients, wrapped tool callbacks and server system contexts. */
    public static class PerRequestTools implements AutoCloseable {

        private final List<McpSyncClient> clients;
        private final ToolCallback[] callbacks;
        private final List<String> serverContexts;
        private final int expectedServers;

        PerRequestTools(List<McpSyncClient> clients, ToolCallback[] callbacks,
                        List<String> serverContexts, int expectedServers) {
            this.clients = clients;
            this.callbacks = callbacks;
            this.serverContexts = serverContexts;
            this.expectedServers = expectedServers;
        }

        public ToolCallback[] getCallbacks() {
            return callbacks;
        }

        /** Returns true when no MCP server connected and no tools are available. */
        public boolean hasNoServers() {
            return expectedServers > 0 && clients.isEmpty();
        }

        public int connectedServers() { return clients.size(); }
        public int expectedServers()  { return expectedServers; }

        /**
         * Returns the combined system-context text contributed by all connected MCP
         * servers, or an empty string if no server exposed the "system-context" prompt.
         */
        public String getServerSystemContext() {
            return String.join("\n\n", serverContexts);
        }

        @Override
        public void close() {
            for (McpSyncClient client : clients) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.warn("Error closing MCP client: {}", e.getMessage());
                }
            }
        }
    }
}
