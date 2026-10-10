package io.mateu.ecdemo1.frontoffice.infra.mcp;

import io.mateu.ecdemo1.agentsql.AgentSql;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The front office's MCP server (Spring AI, SSE at {@code /sse}, as the other services'): the desk's
 * tools, and the "system-context" prompt ia-agent reads from every server it connects to and adds to
 * the agent's system prompt. With agent-sql on, the read-only SQL over its data views too
 * ({@link FrontOfficeDataTools}).
 */
@Configuration
public class McpToolsConfig {

  @Bean
  public ToolCallbackProvider frontDeskToolCallbackProvider(FrontDeskMcpTools tools, ObjectProvider<AgentSql> sql) {
    var agentSql = sql.getIfAvailable();
    return MethodToolCallbackProvider.builder()
        .toolObjects(agentSql == null ? new Object[] {tools} : new Object[] {tools, new FrontOfficeDataTools(agentSql)})
        .build();
  }

  @Bean
  public List<McpServerFeatures.SyncPromptSpecification> systemContextPrompts(FrontDeskMcpTools tools) {
    var prompt = new McpSchema.Prompt("system-context",
        "Domain context for the AI agent about this server's capabilities", List.of());
    return List.of(new McpServerFeatures.SyncPromptSpecification(prompt,
        (exchange, request) -> new McpSchema.GetPromptResult("System context",
            List.of(new McpSchema.PromptMessage(McpSchema.Role.USER,
                new McpSchema.TextContent(tools.systemContext()))))));
  }
}
