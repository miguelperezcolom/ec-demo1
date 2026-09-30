package io.mateu.ecdemo1.iaagent.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iaagent.identity.CallerIdentity;
import io.mateu.ecdemo1.iaagent.observability.TraceHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Resolves the configuration to answer a prompt with, given who is asking and from where — the
 * context-aware path that lets the control plane route to a different agent and refuse an
 * over-budget one.
 *
 * <p>It posts the caller's identity and context to {@code /internal/agents/resolve} and reads the
 * answer three ways. A {@code 200} is the resolved configuration, routed and within budget. A
 * {@code 409} is a deliberate refusal — an unusable agent, a spent budget — and its message is the
 * answer the user should see, so it is returned as a denial and <strong>not</strong> retried or
 * papered over with a cached config, which would be spending past a budget that just said no.
 * Anything else — the control plane unreachable, a 500 — falls back to {@link AgentConfigClient}'s
 * last good configuration of the agent the request asked for (the console's default, which the
 * gateway stamps), or of the catalogue's default when it named none — so a brief outage degrades to
 * "that agent, last known good" rather than a dead panel.
 *
 * <p>Which console the prompt came from ({@code channel}) and its default agent travel with every
 * request; this pod has no agent of its own.
 */
@Component
public class AgentResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentResolver.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final AgentConfigClient configClient;
    private final String url;
    private final TraceHeaders traceHeaders;

    public AgentResolver(AgentConfigClient configClient,
                         @Value("${ia.control-plane.url:http://localhost:8110}") String controlPlaneUrl,
                         TraceHeaders traceHeaders) {
        this.configClient = configClient;
        this.traceHeaders = traceHeaders;
        this.url = controlPlaneUrl.replaceAll("/+$", "") + "/internal/agents/resolve";
    }

    /** A resolved configuration, or a refusal to be shown to the user. Never both. */
    public record Resolution(AgentConfig config, String deniedReason) {
        public boolean allowed() {
            return config != null;
        }

        static Resolution allow(AgentConfig config) {
            return new Resolution(config, null);
        }

        static Resolution deny(String reason) {
            return new Resolution(null, reason);
        }
    }

    private record ResolveRequest(String userId, String username, List<String> roles, String tenant,
                                  String locale, String route, String channel, String defaultAgentId) {
    }

    private record Problem(String agentId, String reason) {}

    /** As before channels: no console and no default agent said — the control plane decides. */
    public Resolution resolve(CallerIdentity caller, String locale, String route) {
        return resolve(caller, locale, route, null, null);
    }

    public Resolution resolve(CallerIdentity caller, String locale, String route, String channel,
                              String defaultAgentId) {
        var id = caller == null ? CallerIdentity.anonymous() : caller;
        var channelSaid = blankToNull(channel);
        var defaultSaid = blankToNull(defaultAgentId);
        try {
            var body = mapper.writeValueAsString(new ResolveRequest(id.userId(), id.username(),
                    id.roles(), id.tenant(), locale, route, channelSaid, defaultSaid));
            var response = http.send(traceHeaders.applyTo(HttpRequest.newBuilder(URI.create(url)))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var config = mapper.readValue(response.body(), AgentConfig.class);
                configClient.remember(config);
                log.debug("Resolved agent {} (channel {}, default {})", config.agentId(), channelSaid, defaultSaid);
                return Resolution.allow(config);
            }
            if (response.statusCode() == 409) {
                // A deliberate refusal. Show its reason; do not fall back — that would answer past a
                // budget or a disabled agent.
                var reason = readReason(response.body());
                log.info("Resolve refused (409): {}", reason);
                return Resolution.deny(reason);
            }
            log.warn("Resolve answered {} — falling back to the last good configuration.",
                    response.statusCode());
            return fallback(defaultSaid);
        } catch (Exception e) {
            log.warn("Resolve unreachable ({}) — falling back to the last good configuration.",
                    e.toString());
            return fallback(defaultSaid);
        }
    }

    /** The last good configuration of the agent asked for, or of the catalogue's default. */
    private Resolution fallback(String defaultAgentId) {
        var agent = defaultAgentId != null ? defaultAgentId : configClient.defaultAgentId();
        var last = configClient.lastGood(agent);
        if (last.isPresent()) {
            return Resolution.allow(last.get());
        }
        try {
            return Resolution.allow(configClient.configOf(agent));
        } catch (AgentConfigClient.Unavailable e) {
            return Resolution.deny("El plano de control no responde y no hay "
                    + "configuración en caché. Inténtalo de nuevo en un momento.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String readReason(String body) {
        try {
            var problem = mapper.readValue(body, Problem.class);
            return problem.reason() != null ? problem.reason() : body;
        } catch (Exception e) {
            return body;
        }
    }
}
