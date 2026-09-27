package io.mateu.ecdemo1.integrations.worker;

import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.integrations.lifecycle.Integrations;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** The onboarding's steps, by step id. Every one idempotent: a step run twice records the same thing. */
@Component
@RequiredArgsConstructor
public class TaskHandlers {

    final Integrations integrations;

    public Optional<Function<TaskExecutionRequested, List<Variable>>> handler(TaskExecutionRequested task) {
        return Optional.ofNullable(switch (task.stepId()) {
            case "verify-connectivity" -> step(integrations::stepVerifyConnectivity);
            case "contrast-catalogues" -> step(integrations::stepContrastCatalogues);
            case "request-mapping" -> step(integrations::stepRequestMapping);
            case "sync-partners" -> step(integrations::stepSyncPartners);
            case "backfill-prepass" -> step(integrations::stepBackfillPrePass);
            case "start-backfill" -> step(integrations::stepStartBackfill);
            case "await-activation" -> step(integrations::stepAwaitActivation);
            case "activate" -> step(integrations::stepActivate);
            default -> null;
        });
    }

    static Function<TaskExecutionRequested, List<Variable>> step(Consumer<String> action) {
        return task -> {
            action.accept(integrationId(task));
            return List.of();
        };
    }

    static String integrationId(TaskExecutionRequested task) {
        return task.variables().stream().filter(v -> ProcessVariables.INTEGRATION_ID.equals(v.name()))
                .map(Variable::value).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Step %s needs the variable %s"
                        .formatted(task.stepId(), ProcessVariables.INTEGRATION_ID)));
    }
}
