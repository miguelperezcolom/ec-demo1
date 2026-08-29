package io.mateu.ecdemo1.iacp.infra.in.rest;

import io.micrometer.core.instrument.MeterRegistry;
import io.mateu.ecdemo1.iacp.application.out.usage.UsageEvent;
import io.mateu.ecdemo1.iacp.application.usecases.usage.RecordUsageUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Where the agent reports what a prompt cost. Two things happen with it: a row is appended for
 * budgets to sum, and a Prometheus counter is advanced for Grafana to chart. The console does not
 * grow a usage screen — Prometheus and Grafana are already in this deployment and are the right
 * place for a time series, so this endpoint feeds them rather than reinventing them.
 *
 * <p>Under {@code /internal}, with the configuration and search endpoints, and under the same rule:
 * no gateway route. It takes a spend figure and a user id from a caller it does not authenticate,
 * so it must stay cluster-internal, exactly like the endpoint that serves the credentials.
 *
 * <p>It answers even when recording fails. A usage report is fire-and-forget from the agent's side;
 * making the agent's next prompt depend on this write succeeding would be the tail wagging the dog.
 * A failure is logged and swallowed with a 202.
 */
@RestController
@RequestMapping("/internal/usage")
@Slf4j
public class UsageController {

    private final RecordUsageUseCase recordUsage;
    private final MeterRegistry meters;

    public UsageController(RecordUsageUseCase recordUsage, MeterRegistry meters) {
        this.recordUsage = recordUsage;
        this.meters = meters;
    }

    /** The shape the agent posts. Restated here rather than shared — a wire contract between pods. */
    public record UsageReport(String agentId, String llmId, String model,
                              int inputTokens, int outputTokens, int totalTokens,
                              String userId, String username, List<String> roles, String tenant,
                              String sessionId) {
    }

    @PostMapping
    public ResponseEntity<Void> record(@RequestBody UsageReport report) {
        try {
            recordUsage.handle(new UsageEvent(report.agentId(), report.llmId(), report.model(),
                    report.inputTokens(), report.outputTokens(), report.totalTokens(),
                    report.userId(), report.username(), report.roles(), report.tenant(),
                    report.sessionId()));
            count(report);
        } catch (Exception e) {
            // Never fail the agent over a metric. The row may be lost; the prompt was still answered.
            log.warn("Could not record usage for agent {}: {}", report.agentId(), e.toString());
        }
        return ResponseEntity.accepted().build();
    }

    private void count(UsageReport r) {
        var agent = r.agentId() == null ? "unknown" : r.agentId();
        var llm = r.llmId() == null ? "unknown" : r.llmId();
        meters.counter("ia.tokens", "agent", agent, "llm", llm, "type", "input")
                .increment(r.inputTokens());
        meters.counter("ia.tokens", "agent", agent, "llm", llm, "type", "output")
                .increment(r.outputTokens());
    }
}
