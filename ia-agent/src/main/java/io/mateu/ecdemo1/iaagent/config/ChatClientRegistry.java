package io.mateu.ecdemo1.iaagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link ChatClient} per credential, built on demand and reused.
 *
 * <p>This class exists because of one asymmetry in Spring AI: the model, the temperature and the
 * max-tokens can be overridden per request through {@code prompt().options(...)}, and the API key
 * cannot. The key lives inside {@code AnthropicApi} / {@code OpenAiApi}, which is
 * constructor-injected into the chat model — so a rotated key is not a parameter change, it is a
 * new client.
 *
 * <p>Hence the cache key: provider, base URL and the key itself. Rotating a credential in the
 * console builds one new client on the next prompt and leaves the old one to be collected;
 * changing a model builds nothing, because that one really is per request.
 *
 * <p>The map is bounded by how many distinct credentials this pod is ever handed, which is one
 * per LLM in the catalogue that some agent it serves is pointed at — a handful, not a leak.
 *
 * <p><strong>Two families, and the OpenAI one is where the catalogue's "OpenAI-compatible"
 * entries land.</strong> Anything that speaks {@code POST /v1/chat/completions} — Ollama, vLLM,
 * an OpenAI-compatible gateway, OpenAI itself — is one {@code OpenAiApi} pointed at a different
 * base URL, so the three catalogue providers collapse into two builders here. Bedrock and Vertex
 * are the ones that genuinely do not fit: their credential is not a single string, which is why
 * the control plane refuses to serve them before this class ever sees one.
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
        if (!isSupported(llm.provider())) {
            throw new UnsupportedProviderException("Agent's LLM '" + llm.name() + "' is a "
                    + llm.provider() + " model. This service calls ANTHROPIC, OPENAI and "
                    + "OPENAI_COMPATIBLE ones.");
        }
        if (llm.apiKey() == null || llm.apiKey().isBlank()) {
            // The control plane refuses to serve an LLM with no credential, so reaching this means
            // the two disagree — worth an explicit message rather than a 401 from the provider.
            throw new UnsupportedProviderException("Agent's LLM '" + llm.name()
                    + "' arrived with no credential.");
        }
        // An OpenAI-compatible entry that names no base URL would silently be sent to OpenAI's own
        // endpoint, where its key and its model id mean nothing — a 401 about a server the
        // operator never pointed at. Say what is missing instead.
        if ("OPENAI_COMPATIBLE".equals(llm.provider()) && isBlank(llm.baseUrl())) {
            throw new UnsupportedProviderException("Agent's LLM '" + llm.name()
                    + "' is OPENAI_COMPATIBLE but names no base URL. That is the address of the "
                    + "server to call; without it there is nothing to be compatible with.");
        }
        var cacheKey = llm.provider() + "|" + (llm.baseUrl() == null ? "" : llm.baseUrl())
                + "|" + llm.apiKey();
        return clients.computeIfAbsent(cacheKey, k -> {
            if ("ANTHROPIC".equals(llm.provider())) {
                log.info("Building a chat client for LLM {} (ANTHROPIC)", llm.name());
                return anthropic(llm);
            }
            // The effective URL, not the one in the catalogue: they differ when the entry carries
            // the /v1 form, and this line is where an operator finds out which one is being called.
            log.info("Building a chat client for LLM {} ({}{})", llm.name(), llm.provider(),
                    isBlank(llm.baseUrl()) ? "" : " at " + openAiBaseUrl(llm.baseUrl()));
            return openAiCompatible(llm);
        });
    }

    private static ChatClient anthropic(AgentConfig.Llm llm) {
        return ChatClient.create(AnthropicChatModel.builder()
                .options(anthropicOptions(llm).build())
                .build());
    }

    /**
     * OpenAI and everything that imitates it. The base URL is the whole of the difference: left
     * empty it is OpenAI's own endpoint, and the completions path stays Spring AI's default
     * {@code /v1/chat/completions}, which is the path these servers agree on.
     */
    private static ChatClient openAiCompatible(AgentConfig.Llm llm) {
        return ChatClient.create(OpenAiChatModel.builder()
                .options(openAiOptions(llm).build())
                .build());
    }

    /**
     * Accepts a base URL written either way, because both are written.
     *
     * <p>The OpenAI SDK's own default is {@code https://api.openai.com/v1} and it appends only
     * {@code chat/completions}, so the version segment belongs in the base URL — the same form
     * {@code OPENAI_BASE_URL} takes everywhere else, and therefore the form that gets pasted into
     * the catalogue. A base URL without it produces a 404 that names nothing.
     *
     * <p>It was the opposite before Spring AI 2.0: its own client appended {@code
     * /v1/chat/completions}, so the {@code /v1} had to be taken off instead. Accepting both forms
     * is what stops that from being a rule anyone has to know, or re-learn.
     */
    static String openAiBaseUrl(String baseUrl) {
        var trimmed = baseUrl.replaceAll("/+$", "");
        return trimmed.endsWith("/v1") ? trimmed : trimmed + "/v1";
    }

    /**
     * The credential travels in the options, and so does everything per-request.
     *
     * <p>Spring AI 2.0 moved the api key and the base URL onto the options object, which is also
     * what the model builds its client from — so one options object is both halves, and the two
     * are built together here rather than being separated by a builder each. The options class
     * still has to match the model it is handed to: a chat model ignores a foreign one, and
     * ignoring it means answering with the provider's defaults instead of the catalogue's model.
     */
    public ChatOptions.Builder<?> optionsFor(AgentConfig.Llm llm) {
        return "ANTHROPIC".equals(llm.provider()) ? anthropicOptions(llm) : openAiOptions(llm);
    }

    private static AnthropicChatOptions.Builder anthropicOptions(AgentConfig.Llm llm) {
        var builder = AnthropicChatOptions.builder();
        // Native since 2.0, and it replaces the request interceptor that used to rewrite the
        // system prompt into cache_control blocks by hand — that interceptor was written against
        // a RestClient this version no longer uses, so it would have gone silently inert.
        builder.cacheOptions(AnthropicCacheOptions.builder()
                .strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
                .build());
        builder.apiKey(llm.apiKey());
        builder.model(llm.model());
        if (!isBlank(llm.baseUrl())) {
            builder.baseUrl(llm.baseUrl());
        }
        if (llm.temperature() != null) {
            builder.temperature(llm.temperature());
        }
        if (llm.maxTokens() != null) {
            builder.maxTokens(llm.maxTokens());
        }
        return builder;
    }

    private static OpenAiChatOptions.Builder openAiOptions(AgentConfig.Llm llm) {
        var builder = OpenAiChatOptions.builder();
        builder.apiKey(llm.apiKey());
        builder.model(llm.model());
        if (!isBlank(llm.baseUrl())) {
            builder.baseUrl(openAiBaseUrl(llm.baseUrl()));
        }
        if (llm.temperature() != null) {
            builder.temperature(llm.temperature());
        }
        if (llm.maxTokens() != null) {
            builder.maxTokens(llm.maxTokens());
        }
        return builder;
    }

    private static boolean isSupported(String provider) {
        return "ANTHROPIC".equals(provider)
                || "OPENAI".equals(provider)
                || "OPENAI_COMPATIBLE".equals(provider);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static class UnsupportedProviderException extends RuntimeException {
        public UnsupportedProviderException(String message) { super(message); }
    }
}
