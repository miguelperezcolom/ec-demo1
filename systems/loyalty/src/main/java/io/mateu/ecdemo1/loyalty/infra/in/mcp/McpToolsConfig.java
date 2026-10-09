package io.mateu.ecdemo1.loyalty.infra.in.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider loyaltyToolCallbackProvider(LoyaltyMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    /** The "system-context" prompt ia-agent collects from every MCP server it connects to. */
    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> systemContextPrompts(LoyaltyMcpTools tools) {
        var prompt = new McpSchema.Prompt("system-context",
                "Domain context for the AI agent about this server's capabilities", List.of());
        return List.of(new McpServerFeatures.SyncPromptSpecification(prompt,
                (exchange, request) -> new McpSchema.GetPromptResult("System context",
                        List.of(new McpSchema.PromptMessage(McpSchema.Role.USER,
                                new McpSchema.TextContent(tools.getSystemContext()))))));
    }
}
