package io.mateu.ecdemo1.integrations.worker;

import io.mateu.ecdemo1.integrations.frontoffice.FrontOfficeIntegrations;
import io.mateu.ecdemo1.integrations.lifecycle.Integrations;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import io.mateu.workflow.worker.api.Cancellations;
import io.mateu.workflow.worker.api.TaskDispatcher;
import io.mateu.workflow.worker.api.TaskRegistration;
import io.mateu.workflow.worker.api.TaskRegistry;
import io.mateu.workflow.worker.api.TaskTracing;
import io.mateu.workflow.worker.api.TaskReplySink;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The onboarding's steps, run through the engine's worker runtime as the contracts they serve. */
class IntegrationsTasksTest {

    /** What the steps were asked to do, in order. */
    final List<String> ran = new ArrayList<>();

    final Integrations integrations = new Integrations(null, null, null, null, null, null, null, null, null) {
        @Override
        public void stepVerifyConnectivity(String id) {
            if ("DOWN".equals(id)) {
                throw new IllegalStateException("The PMS does not answer");
            }
            ran.add("verify-connectivity " + id);
        }

        @Override
        public void stepSyncPartners(String id) {
            ran.add("sync-partners " + id);
        }

        @Override
        public void stepActivate(String id) {
            ran.add("activate " + id);
        }
    };
    final FrontOfficeIntegrations frontOffices = new FrontOfficeIntegrations(null, null, null, null, null, null, null, null, null) {
        @Override
        public void stepActivate(String id) {
            ran.add("fo-activate " + id);
        }
    };
    final TaskHandlers handlers = new TaskHandlers(integrations, frontOffices);
    final IntegrationsTasks tasks = new IntegrationsTasks();
    final RecordingSink sink = new RecordingSink();
    final List<TaskRegistration<?, ?>> registrations = registrations();
    final TaskDispatcher dispatcher = new TaskDispatcher(new TaskRegistry(registrations), sink, Cancellations.NONE, false, TaskTracing.NOOP);

    /** Every @Bean method of IntegrationsTasks that makes a registration, as Spring would call them. */
    List<TaskRegistration<?, ?>> registrations() {
        return Arrays.stream(IntegrationsTasks.class.getDeclaredMethods())
                .filter(m -> m.getReturnType() == TaskRegistration.class && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == TaskHandlers.class)
                .map(m -> {
                    try {
                        return (TaskRegistration<?, ?>) m.invoke(tasks, handlers);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .<TaskRegistration<?, ?>>map(r -> r)
                .toList();
    }

    static class RecordingSink implements TaskReplySink {
        final List<String> replies = new ArrayList<>();

        @Override
        public void running(TaskExecutionRequested task) {
        }

        @Override
        public void completed(TaskExecutionRequested task, List<Variable> variables) {
            replies.add("COMPLETED " + variables);
        }

        @Override
        public void failed(TaskExecutionRequested task, List<Variable> variables, String reason) {
            replies.add("ERROR " + reason);
        }
    }

    static TaskExecutionRequested task(String step, String taskId, Variable... variables) {
        return new TaskExecutionRequested("TE-1", "PROC-1", "alta-integracion", step, taskId, List.of(variables));
    }

    @Test
    void everyStepOfBothOnboardingsHasItsContract() {
        assertThat(registrations).extracting(TaskRegistration::ref).containsExactlyInAnyOrderElementsOf(
                IntegrationsTasks.IDS.stream().map(id -> id + "@1").toList());
        assertThat(registrations).extracting(TaskRegistration::topic).containsOnly("integrations");
    }

    @Test
    void aContractTaskRunsItsStepWithTheIntegrationsId() {
        dispatcher.dispatch(task("activate", "activate@1", new Variable("integrationId", "INT-7"),
                new Variable("processKey", "alta-integracion:INT-7")));
        dispatcher.dispatch(task("fo-activate", "fo-activate@1", new Variable("integrationId", "FO-3")));

        assertThat(ran).containsExactly("activate INT-7", "fo-activate FO-3");
        assertThat(sink.replies).containsExactly("COMPLETED []", "COMPLETED []");
    }

    @Test
    void aStepFromAProcessThatPredatesTheContractsRunsAsTheContractItsStepIsNamedAfter() {
        // No taskId: the runtime (2.23.1) serves the step id as a contract id. A step no contract
        // here is named after is not served, and a taskId that is set never falls back to the step.
        dispatcher.dispatch(task("sync-partners", "", new Variable("integrationId", "INT-7")));
        dispatcher.dispatch(task("prepare", ""));
        dispatcher.dispatch(task("sync-partners", "sync-partners@2", new Variable("integrationId", "INT-7")));

        assertThat(ran).containsExactly("sync-partners INT-7");
        assertThat(sink.replies).containsExactly("COMPLETED []");
    }

    @Test
    void aStepWithoutItsIntegrationFailsWithTheReasonItAlwaysGave() {
        dispatcher.dispatch(task("activate", "activate@1"));

        assertThat(ran).isEmpty();
        assertThat(sink.replies).containsExactly("ERROR Step activate needs the variable integrationId");
    }

    @Test
    void aFailingStepReportsItsMessage() {
        dispatcher.dispatch(task("verify-connectivity", "verify-connectivity@1", new Variable("integrationId", "DOWN")));

        assertThat(sink.replies).containsExactly("ERROR The PMS does not answer");
    }
}
