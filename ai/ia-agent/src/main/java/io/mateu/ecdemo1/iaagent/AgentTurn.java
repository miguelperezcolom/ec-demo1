package io.mateu.ecdemo1.iaagent;

import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.mateu.ecdemo1.iaagent.config.ChatClientRegistry;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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

        var usage = usageOf(chatResponse);
        return new Result(textOf(chatResponse), usage[0], usage[1], usage[2]);
    }

    /**
     * The same turn, as it is produced: one {@link ChatResponse} per chunk of text the model sends,
     * through every round of the tool loop. Each chunk's text is a delta, not the answer so far;
     * the usage on a chunk is what the turn has cost up to it — Spring AI's tool-calling advisor
     * carries the earlier rounds' usage onto the later ones — so the last chunk that has any is
     * the turn's total.
     *
     * <p>Tools are run by that advisor on {@code Schedulers.boundedElastic()}, never on the thread
     * that delivers the model's chunks, and the MCP ones are moved to their own pool besides — so a
     * tool that blocks holds a worker, not an event loop.
     *
     * <p>Cold: nothing is sent to the model until it is subscribed to. A misconfigured LLM fails the
     * subscription, not this call.
     */
    public Flux<ChatResponse> stream(AgentConfig config, String systemPrompt, List<Message> history,
                                     String userMessage, ToolCallback[] tools) {
        return Flux.defer(() -> chatClients.forLlm(config.llm()).prompt()
                .options(chatClients.optionsFor(config.llm()))
                .system(systemPrompt)
                .messages(history)
                .user(userMessage)
                .toolCallbacks(tools)
                .stream()
                .chatResponse());
    }

    /** The text of a response — the whole answer of a call, or one chunk of a stream. Null when none. */
    public static String textOf(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        var result = chatResponse.getResult();
        return result != null && result.getOutput() != null ? result.getOutput().getText() : null;
    }

    /** Input, output and total tokens, as the response states them; zeros when it states none. */
    public static int[] usageOf(ChatResponse chatResponse) {
        var usage = chatResponse != null && chatResponse.getMetadata() != null
                ? chatResponse.getMetadata().getUsage() : null;
        if (usage == null) {
            return new int[] {0, 0, 0};
        }
        return new int[] {
                usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0,
                usage.getTotalTokens() != null ? usage.getTotalTokens() : 0};
    }
}
