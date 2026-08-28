package io.mateu.ecdemo1.iaagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link ChatClient} per credential, built on demand and reused.
 *
 * <p>This class exists because of one asymmetry in Spring AI: the model, the temperature and the
 * max-tokens can be overridden per request through {@code prompt().options(...)}, and the API key
 * cannot. The key lives inside {@code AnthropicApi}, which is constructor-injected into
 * {@code AnthropicChatModel} — so a rotated key is not a parameter change, it is a new client.
 *
 * <p>Hence the cache key: provider, base URL and the key itself. Rotating a credential in the
 * console builds one new client on the next prompt and leaves the old one to be collected;
 * changing a model builds nothing, because that one really is per request.
 *
 * <p>The map is bounded by how many distinct credentials this pod is ever handed, which is one
 * per LLM in the catalogue that some agent it serves is pointed at — a handful, not a leak.
 */
@Component
public class ChatClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatClientRegistry.class);

    private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();

    /**
     * @throws UnsupportedProviderException for a provider this service cannot call. The catalogue
     *         can hold one — an entry may be written before it is usable — so this has to be an
     *         answerable error rather than something that stops the pod.
     */
    public ChatClient forLlm(AgentConfig.Llm llm) {
        if (!"ANTHROPIC".equals(llm.provider())) {
            throw new UnsupportedProviderException("Agent's LLM '" + llm.name() + "' is a "
                    + llm.provider() + " model. This service only calls ANTHROPIC ones.");
        }
        if (llm.apiKey() == null || llm.apiKey().isBlank()) {
            // The control plane refuses to serve an LLM with no credential, so reaching this means
            // the two disagree — worth an explicit message rather than a 401 from Anthropic.
            throw new UnsupportedProviderException("Agent's LLM '" + llm.name()
                    + "' arrived with no credential.");
        }
        var cacheKey = llm.provider() + "|" + (llm.baseUrl() == null ? "" : llm.baseUrl())
                + "|" + llm.apiKey();
        return clients.computeIfAbsent(cacheKey, k -> {
            log.info("Building a chat client for LLM {} ({})", llm.name(), llm.provider());
            var apiBuilder = AnthropicApi.builder().apiKey(llm.apiKey());
            if (llm.baseUrl() != null && !llm.baseUrl().isBlank()) {
                apiBuilder.baseUrl(llm.baseUrl());
            }
            return ChatClient.create(AnthropicChatModel.builder()
                    .anthropicApi(apiBuilder.build())
                    .build());
        });
    }

    /**
     * The per-request half: model, temperature and max tokens, which do not need a new client.
     */
    public AnthropicChatOptions optionsFor(AgentConfig.Llm llm) {
        var builder = AnthropicChatOptions.builder().model(llm.model());
        if (llm.temperature() != null) {
            builder.temperature(llm.temperature());
        }
        if (llm.maxTokens() != null) {
            builder.maxTokens(llm.maxTokens());
        }
        return builder.build();
    }

    public static class UnsupportedProviderException extends RuntimeException {
        public UnsupportedProviderException(String message) { super(message); }
    }
}
