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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Where this agent's model, prompt and tool list come from: the control plane, not this pod's
 * configuration file.
 *
 * <p><strong>Cached for {@code TTL}, and the last good answer outlives the control plane.</strong>
 * Those are two separate decisions and both matter.
 *
 * <p>The cache is what keeps the control plane off the hot path — a burst of prompts is one fetch,
 * not one per message — and 30 seconds is short enough that changing a model in the console shows
 * up in the next prompt or two, which is what an operator expects from a control plane.
 *
 * <p>Serving the stale copy when the fetch fails is the more important half. The alternative is a
 * chat panel that goes down because a catalogue is briefly unreachable, and there is no version of
 * that trade that is worth taking: the configuration a moment ago is almost certainly still right.
 * It is logged at warn every time, so "running on stale configuration" is visible rather than
 * silent, and {@link #lastFetchFailed()} lets the health endpoint say so too.
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

    private final String url;
    private final String agentId;

    /** Last good configuration and when it was fetched. Null until the first success. */
    private final AtomicReference<Cached> cache = new AtomicReference<>();
    private final AtomicReference<String> lastFailure = new AtomicReference<>();

    private record Cached(AgentConfig config, Instant fetchedAt) {}

    public AgentConfigClient(
            @Value("${ia.control-plane.url:http://localhost:8110}") String controlPlaneUrl,
            @Value("${ia.agent-id:console-agent}") String agentId) {
        this.agentId = agentId;
        this.url = controlPlaneUrl.replaceAll("/+$", "")
                + "/internal/agents/" + agentId + "/config";
        log.info("Agent configuration comes from {}", url);
    }

    /**
     * The configuration to answer the next prompt with, or empty when the control plane has never
     * been reached. Empty means this pod cannot serve anything, which is what the health
     * indicator reports and what keeps it out of the Service's endpoints.
     */
    public Optional<AgentConfig> current() {
        var cached = cache.get();
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(TTL) < 0) {
            return Optional.of(cached.config());
        }
        return Optional.ofNullable(refresh(cached));
    }

    /** Refreshes, falling back to {@code stale} — which may be null — on any failure. */
    private AgentConfig refresh(Cached stale) {
        try {
            var response = http.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(REQUEST_TIMEOUT)
                            .header("Accept", "application/json")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var config = mapper.readValue(response.body(), AgentConfig.class);
                cache.set(new Cached(config, Instant.now()));
                lastFailure.set(null);
                if (config.warnings() != null && !config.warnings().isEmpty()) {
                    // The control plane already dropped what it could not resolve. Repeating the
                    // warnings here is what makes them visible in this pod's logs, next to the
                    // prompts that were answered with fewer tools than the catalogue implies.
                    log.warn("Agent {} resolved with warnings: {}", agentId, config.warnings());
                }
                log.info("Agent {} configuration refreshed: model {}, {} MCP server(s)",
                        agentId, config.llm().model(), config.mcps().size());
                return config;
            }
            // 409 is the control plane refusing to serve this agent — disabled, no credential, no
            // such LLM. Its body says which, and that message is the whole diagnosis.
            return failed("control plane answered " + response.statusCode() + ": " + response.body(), stale);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()), stale);
        }
    }

    private AgentConfig failed(String reason, Cached stale) {
        lastFailure.set(reason);
        if (stale != null) {
            log.warn("Could not refresh agent {} configuration ({}). Serving the copy from {} —"
                    + " this pod is running on stale configuration.", agentId, reason, stale.fetchedAt());
            return stale.config();
        }
        log.error("Could not fetch agent {} configuration and there is nothing cached ({}). "
                + "This pod cannot answer prompts until the control plane is reachable and the "
                + "agent is servable.", agentId, reason);
        return null;
    }

    public String agentId() {
        return agentId;
    }

    /** Null when the last fetch succeeded; the reason otherwise. */
    public String lastFetchFailed() {
        return lastFailure.get();
    }

    /** Whether a configuration has ever been resolved. */
    public boolean hasEverResolved() {
        return cache.get() != null;
    }
}
