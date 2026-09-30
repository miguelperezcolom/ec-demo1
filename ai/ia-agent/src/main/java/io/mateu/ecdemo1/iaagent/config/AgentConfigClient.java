package io.mateu.ecdemo1.iaagent.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Where the agents' model, prompt and tool list come from: the control plane, not this pod's
 * configuration file — and not a fixed agent either. This pod serves every agent of the catalogue:
 * which one answers a prompt is decided per request (the gateway stamps the console's default agent,
 * the control plane's routes may pick another), so nothing here is "this pod's agent".
 *
 * <p><strong>Cached for {@code TTL} per agent, and the last good answer outlives the control
 * plane.</strong> The cache keeps the control plane off the hot path; serving the stale copy when a
 * fetch fails keeps a chat panel up while the catalogue is briefly unreachable. Every configuration
 * the control plane hands out — by id, or resolved by context — is remembered under its agent's id,
 * so the fallback for a prompt is the last good configuration of the agent it asked for.
 *
 * <p>The catalogue's own default agent (what answers a prompt that names none) is asked of the
 * control plane too, and remembered the same way.
 *
 * <p>What it deliberately does not do is fall back to a locally configured model. There is one
 * source of truth, and a second one that only appears when the first is unreachable is how two
 * configurations quietly diverge.
 */
@Component
public class AgentConfigClient {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigClient.class);

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper mapper = new ObjectMapper()
            // The control plane may add fields; this one should not care.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String controlPlaneUrl;

    /** Every agent's last good configuration, by id. */
    private final Map<String, Cached> configs = new ConcurrentHashMap<>();

    /** The catalogue's default agent, as last said, and when. */
    private final AtomicReference<CachedId> defaultAgent = new AtomicReference<>();
    private final AtomicReference<String> lastFailure = new AtomicReference<>();

    private record Cached(AgentConfig config, Instant fetchedAt) {}

    private record CachedId(String agentId, Instant fetchedAt) {}

    public AgentConfigClient(@Value("${ia.control-plane.url:http://localhost:8110}") String controlPlaneUrl) {
        this.controlPlaneUrl = controlPlaneUrl.replaceAll("/+$", "");
        log.info("Agent configurations come from {}/internal/agents", this.controlPlaneUrl);
    }

    /** Remembers a configuration the control plane handed out, under its agent's id. */
    public void remember(AgentConfig config) {
        if (config != null && config.agentId() != null) {
            configs.put(config.agentId(), new Cached(config, Instant.now()));
        }
    }

    /** The last good configuration of an agent, without asking anyone — the fallback's answer. */
    public Optional<AgentConfig> lastGood(String agentId) {
        var cached = agentId == null ? null : configs.get(agentId);
        return cached == null ? Optional.empty() : Optional.of(cached.config());
    }

    /**
     * The catalogue's default agent: the control plane's answer, kept for {@code TTL}, and the last
     * one known when it does not answer. Null when it has never answered.
     */
    public String defaultAgentId() {
        var cached = defaultAgent.get();
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(TTL) < 0) {
            return cached.agentId();
        }
        try {
            var response = http.send(
                    HttpRequest.newBuilder(URI.create(controlPlaneUrl + "/internal/agents/default"))
                            .timeout(REQUEST_TIMEOUT)
                            .header("Accept", "application/json")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var id = mapper.readTree(response.body()).path("agentId").asText(null);
                defaultAgent.set(new CachedId(id, Instant.now()));
                lastFailure.set(null);
                return id;
            }
            lastFailure.set("control plane answered " + response.statusCode() + " for the default agent");
        } catch (Exception e) {
            lastFailure.set(e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
        if (cached != null) {
            log.warn("Could not ask the control plane for the default agent ({}); keeping '{}'",
                    lastFailure.get(), cached.agentId());
            return cached.agentId();
        }
        return null;
    }

    /**
     * Any agent's configuration, by id — what the A2A endpoint answers with, and the fallback of a
     * prompt whose resolve failed. The same TTL and the same last-good fallback for every agent.
     *
     * @throws Unavailable with the control plane's reason when there is nothing to serve — a 409
     *         (disabled, no usable model) or never reachable. A 409 is not papered over with a
     *         stale copy: the catalogue said no, and the caller should hear that.
     */
    public AgentConfig configOf(String requestedAgentId) {
        if (requestedAgentId == null || requestedAgentId.isBlank()) {
            throw new Unavailable("No agent named, and the control plane did not say which answers");
        }
        var cached = configs.get(requestedAgentId);
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(TTL) < 0) {
            return cached.config();
        }
        String reason;
        try {
            var response = http.send(
                    HttpRequest.newBuilder(URI.create(controlPlaneUrl + "/internal/agents/"
                                    + java.net.URLEncoder.encode(requestedAgentId,
                                    java.nio.charset.StandardCharsets.UTF_8) + "/config"))
                            .timeout(REQUEST_TIMEOUT)
                            .header("Accept", "application/json")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var config = mapper.readValue(response.body(), AgentConfig.class);
                configs.put(requestedAgentId, new Cached(config, Instant.now()));
                lastFailure.set(null);
                if (config.warnings() != null && !config.warnings().isEmpty()) {
                    log.warn("Agent {} resolved with warnings: {}", requestedAgentId, config.warnings());
                }
                log.info("Agent {} configuration refreshed: model {}, {} MCP server(s)",
                        requestedAgentId, config.llm().model(), config.mcps().size());
                return config;
            }
            if (response.statusCode() == 409) {
                configs.remove(requestedAgentId);
                throw new Unavailable(reasonOf(response.body()));
            }
            reason = "control plane answered " + response.statusCode();
        } catch (Unavailable e) {
            throw e;
        } catch (Exception e) {
            reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
        lastFailure.set(reason);
        if (cached != null) {
            log.warn("Could not refresh agent {} configuration ({}). Serving the copy from {} —"
                    + " running on stale configuration.", requestedAgentId, reason, cached.fetchedAt());
            return cached.config();
        }
        throw new Unavailable("Agent '" + requestedAgentId + "' has no configuration: " + reason);
    }

    private String reasonOf(String body) {
        try {
            var reason = mapper.readTree(body).path("reason").asText(null);
            return reason != null ? reason : body;
        } catch (Exception e) {
            return body;
        }
    }

    /** No configuration to answer with, and the sentence that says why. */
    public static class Unavailable extends RuntimeException {
        public Unavailable(String message) { super(message); }
    }

    /** Null when the last call to the control plane succeeded; the reason otherwise. */
    public String lastFetchFailed() {
        return lastFailure.get();
    }

    /** Whether the control plane has ever answered, or any configuration is held. */
    public boolean hasEverResolved() {
        return defaultAgent.get() != null || !configs.isEmpty();
    }
}
