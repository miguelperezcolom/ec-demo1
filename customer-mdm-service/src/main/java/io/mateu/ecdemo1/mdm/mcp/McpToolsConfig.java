package io.mateu.ecdemo1.mdm.mcp;

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
    public ToolCallbackProvider customerToolCallbackProvider(CustomerMcpTools customerTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(customerTools)
                .build();
    }

    /**
     * Exposes a "system-context" MCP Prompt so the ia-agent-service can build a
     * dynamic system prompt by collecting context from every connected MCP server.
     */
    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> systemContextPrompts(CustomerMcpTools customerTools) {
        var prompt = new McpSchema.Prompt(
                "system-context",
                "Domain context for the AI agent about this server's capabilities",
                List.of());

        return List.of(new McpServerFeatures.SyncPromptSpecification(prompt,
                (exchange, request) -> new McpSchema.GetPromptResult(
                        "System context",
                        List.of(new McpSchema.PromptMessage(
                                McpSchema.Role.USER,
                                new McpSchema.TextContent(customerTools.getSystemContext()))))));
    }
}
