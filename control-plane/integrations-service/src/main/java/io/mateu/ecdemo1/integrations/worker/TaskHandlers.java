package io.mateu.ecdemo1.integrations.worker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.integrations.frontoffice.FrontOfficeIntegrations;
import io.mateu.ecdemo1.integrations.lifecycle.Integrations;
import io.mateu.workflow.worker.api.TaskContext;
import io.mateu.workflow.worker.api.TaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The onboarding's steps, one per task contract (ec-definitions, definitions/tasks). Every one
 * idempotent: a step run twice records the same thing.
 */
@Component
@RequiredArgsConstructor
public class TaskHandlers {

    /** The input of every onboarding task: which integration it is about. A task carries every variable of its process; the rest are not its. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Onboarding(String integrationId) {
    }

    final Integrations integrations;
    final FrontOfficeIntegrations frontOffices;

    /** The step of «alta-integracion» that takes this integration's id. */
    public TaskHandler<Onboarding, Void> onboarding(BiConsumer<Integrations, String> step) {
        return (input, task) -> run(input, task, id -> step.accept(integrations, id));
    }

    /** The step of «alta-integracion-fo» — the front office fed from the PMS — that takes its id. */
    public TaskHandler<Onboarding, Void> frontOffice(BiConsumer<FrontOfficeIntegrations, String> step) {
        return (input, task) -> run(input, task, id -> step.accept(frontOffices, id));
    }

    static Void run(Onboarding input, TaskContext task, Consumer<String> action) {
        action.accept(integrationId(input, task));
        return null;
    }

    static String integrationId(Onboarding input, TaskContext task) {
        if (input == null || input.integrationId() == null) {
            throw new IllegalArgumentException("Step %s needs the variable %s"
                    .formatted(task.stepId(), ProcessVariables.INTEGRATION_ID));
        }
        return input.integrationId();
    }
}
