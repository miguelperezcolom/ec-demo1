package io.mateu.ecdemo1.contenthub.infra.in.mcp;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * One MCP server, "intranet": the intranet's tools and the newsletters', over two transports.
 *
 * <ul>
 *   <li><b>SSE</b> (Spring AI's autoconfiguration, {@code spring.ai.mcp.server.*}): the stream at {@code /sse},
 *       the messages at {@code /_intranet/mcp/message}. ia-agent connects to {@code http://content-hub:8135/sse}
 *       inside the cluster; a desktop client to {@code https://<host>/_intranet/mcp/sse}, which the gateway
 *       sends to {@code /sse}. The stream's first event tells the client where to post its messages — a
 *       path, resolved against the host the client connected to — so that path is one the gateway routes
 *       unchanged ({@code /_intranet/**}) and this pod serves too: right from both sides.</li>
 *   <li><b>Streamable HTTP</b> (built here, alongside — the autoconfiguration builds one transport only): one
 *       endpoint, {@code /mcp}, at {@code https://<host>/_intranet/mcp} through the gateway. No event names a
 *       URL, so the gateway's rewrite is all it takes. What current desktop clients prefer.</li>
 * </ul>
 * Both with the caller's Keycloak token, verified by SecurityConfig, and the same tools: each call is the
 * caller's own (McpCaller).
 */
@Configuration
public class McpConfig {

    public static final String STREAMABLE_ENDPOINT = "/mcp";

    @Bean
    public ToolCallbackProvider intranetTools(IntranetMcpTools tools, NewsletterMcpTools newsletters) {
        return MethodToolCallbackProvider.builder().toolObjects(tools, newsletters).build();
    }

    /** The "system-context" prompt ia-agent collects from every MCP server it connects to. */
    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> systemContextPrompts(IntranetMcpTools tools) {
        var prompt = new McpSchema.Prompt("system-context", "Domain context for the AI agent about this server's capabilities",
                List.of());
        return List.of(new McpServerFeatures.SyncPromptSpecification(prompt,
                (exchange, request) -> new McpSchema.GetPromptResult("System context",
                        List.of(new McpSchema.PromptMessage(McpSchema.Role.USER,
                                new McpSchema.TextContent(tools.getSystemContext()))))));
    }

    /** The streamable HTTP transport and its server; not an McpSyncServer bean, which would replace the SSE one. */
    @Bean
    public StreamableMcp streamableMcp(@Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
                                       @Qualifier("intranetTools") ToolCallbackProvider tools,
                                       @Qualifier("systemContextPrompts") List<McpServerFeatures.SyncPromptSpecification> prompts,
                                       @Value("${spring.ai.mcp.server.name:content-hub}") String name,
                                       @Value("${spring.ai.mcp.server.version:1.0.0}") String version,
                                       @Value("${spring.ai.mcp.server.request-timeout:20s}") Duration requestTimeout) {
        var transport = WebMvcStreamableServerTransportProvider.builder().jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(STREAMABLE_ENDPOINT).build();
        var server = McpServer.sync(transport)
                .serverInfo(name, version)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).prompts(false).build())
                .tools(McpToolUtils.toSyncToolSpecification(List.of(tools.getToolCallbacks())))
                .prompts(prompts)
                .requestTimeout(requestTimeout)
                // on the request's thread, as the SSE server: the tools read the caller's token from the request
                .immediateExecution(true)
                .build();
        return new StreamableMcp(transport, server);
    }

    @Bean
    public RouterFunction<ServerResponse> streamableMcpRouterFunction(StreamableMcp mcp) {
        return mcp.transport().getRouterFunction();
    }

    public record StreamableMcp(WebMvcStreamableServerTransportProvider transport, McpSyncServer server)
            implements DisposableBean {
        @Override
        public void destroy() {
            server.closeGracefully();
        }
    }
}
