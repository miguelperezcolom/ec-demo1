package io.mateu.ecdemo1.iaagent.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iaagent.identity.CallerIdentity;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reports what a prompt cost to the control plane, off the answer's path.
 *
 * <p><strong>Fire-and-forget, and that is deliberate.</strong> The user has already been answered
 * by the time there are token counts to report; making them wait on a POST to the control plane, or
 * fail their prompt because it was briefly unreachable, would be charging the conversation for the
 * bookkeeping. So the send happens on a small executor and its failure is logged, never raised. The
 * cost of that trade is a lost usage row on an outage — which slightly under-counts a budget, an
 * error in the spender's favour, and the acceptable direction for it to be wrong.
 */
@Component
public class UsageReporter {

    private static final Logger log = LoggerFactory.getLogger(UsageReporter.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "usage-reporter");
        t.setDaemon(true);
        return t;
    });
    private final String url;

    public UsageReporter(@Value("${ia.control-plane.url:http://localhost:8110}") String controlPlaneUrl) {
        this.url = controlPlaneUrl.replaceAll("/+$", "") + "/internal/usage";
    }

    /** The shape the control plane's UsageController accepts. A wire contract between two pods. */
    public record UsageReport(String agentId, String llmId, String model,
                              int inputTokens, int outputTokens, int totalTokens,
                              String userId, String username, List<String> roles, String tenant,
                              String sessionId) {
    }

    public void report(String agentId, String llmId, String model,
                       int inputTokens, int outputTokens, int totalTokens,
                       CallerIdentity caller, String sessionId) {
        var id = caller == null ? CallerIdentity.anonymous() : caller;
        var report = new UsageReport(agentId, llmId, model, inputTokens, outputTokens, totalTokens,
                id.userId(), id.username(), id.roles(), id.tenant(), sessionId);
        worker.execute(() -> send(report));
    }

    private void send(UsageReport report) {
        try {
            var body = mapper.writeValueAsString(report);
            var response = http.send(HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                log.warn("Usage report for agent {} refused: HTTP {}", report.agentId(),
                        response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Could not report usage for agent {}: {}", report.agentId(), e.toString());
        }
    }
}
