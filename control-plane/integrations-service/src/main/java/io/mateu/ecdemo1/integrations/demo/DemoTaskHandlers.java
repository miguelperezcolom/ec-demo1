package io.mateu.ecdemo1.integrations.demo;

import io.mateu.workflow.worker.api.TaskContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * This service's steps of reset-demo (ec-definitions): the engine to zero, the health check, and the
 * notice with the result. Each one idempotent.
 */
@Component
@RequiredArgsConstructor
public class DemoTaskHandlers {

    public record Purge(String processKey) {
    }

    public record Purged(String enginePurged) {
    }

    public record HealthInput(String processKey, String operaContext) {
    }

    public record HealthOutput(String healthOk, String healthSummary) {
    }

    public record NoticeInput(String processKey, String launchedBy, String operaContext, String salesforceDeleted,
                              String enginePurged, String seededBookings, String healthOk, String healthSummary) {
    }

    final EnginePurge purge;
    final DemoHealth health;
    final ResetNotice notice;
    final ResetRuns runs;
    final DemoProperties properties;

    /** Every process but this one goes: the reset that asks keeps running. */
    public Purged purgeEngine(Purge input, TaskContext task) {
        return new Purged(String.valueOf(purge.purgeAllBut(task.processId())));
    }

    public HealthOutput checkHealth(HealthInput input, TaskContext task) {
        var result = health.check(input == null ? null : input.operaContext());
        task.progress(result.summary());
        return new HealthOutput(String.valueOf(result.ok()), result.summary());
    }

    public Void notifyResult(NoticeInput input, TaskContext task) {
        if (input == null || input.processKey() == null) {
            throw new IllegalArgumentException("Step " + task.stepId() + " needs the variable processKey");
        }
        var confirmedBy = runs.byKey(input.processKey()).map(ResetRuns.Run::confirmedBy).orElse(null);
        notice.notify(new ResetNotice.Result(input.processKey(), input.launchedBy(), confirmedBy, input.operaContext(),
                input.salesforceDeleted(), input.enginePurged(), input.seededBookings(), "true".equals(input.healthOk()),
                input.healthSummary(), properties.processLink(task.processId())));
        return null;
    }
}
