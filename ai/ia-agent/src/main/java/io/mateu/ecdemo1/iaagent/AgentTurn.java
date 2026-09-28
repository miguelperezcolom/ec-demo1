package io.mateu.ecdemo1.iaagent;

import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.mateu.ecdemo1.iaagent.config.ChatClientRegistry;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One turn of an agent: its model, called with a system prompt, the history, the new message and
 * the tools, until it answers. The chat panel's two endpoints and the A2A endpoint all answer
 * through this, so an agent reached by another agent is the same agent a person talks to.
 */
@Component
public class AgentTurn {

    private final ChatClientRegistry chatClients;

    public AgentTurn(ChatClientRegistry chatClients) {
        this.chatClients = chatClients;
    }

    /** The answer, and what the last response said it cost. Content is null when there was none. */
    public record Result(String content, int inputTokens, int outputTokens, int totalTokens) {
    }

    public Result call(AgentConfig config, String systemPrompt, List<Message> history,
                       String userMessage, ToolCallback[] tools) {
        var chatResponse = chatClients.forLlm(config.llm()).prompt()
                .options(chatClients.optionsFor(config.llm()))
                .system(systemPrompt)
                .messages(history)
                .user(userMessage)
                .toolCallbacks(tools)
                .call()
                .chatResponse();

        String content = null;
        int inputTokens = 0, outputTokens = 0, totalTokens = 0;
        if (chatResponse != null) {
            var result = chatResponse.getResult();
            if (result != null && result.getOutput() != null) {
                content = result.getOutput().getText();
            }
            var usage = chatResponse.getMetadata() != null ? chatResponse.getMetadata().getUsage() : null;
            if (usage != null) {
                inputTokens  = usage.getPromptTokens()     != null ? usage.getPromptTokens()     : 0;
                outputTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                totalTokens  = usage.getTotalTokens()      != null ? usage.getTotalTokens()      : 0;
            }
        }
        return new Result(content, inputTokens, outputTokens, totalTokens);
    }
}
