package io.mateu.ecdemo1.integrations.worker;

import io.mateu.ecdemo1.integrations.frontoffice.FrontOfficeIntegrations;
import io.mateu.ecdemo1.integrations.lifecycle.Integrations;
import io.mateu.ecdemo1.integrations.worker.runtime.Reasons;
import io.mateu.workflow.worker.api.TaskHandler;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * The tasks this service serves — the steps of «alta-integracion» and «alta-integracion-fo» — each
 * bound to its contract in ec-definitions ({@code definitions/tasks/<id>.ectask}): the engine's worker
 * runtime (worker-kafka) collects these registrations and dispatches every {@code <id>@<version>} it
 * receives on the {@value #TOPIC} topic to the handler here. The contract ids are the steps' own.
 */
@Configuration
public class IntegrationsTasks {

    public static final String TOPIC = "integrations";

    /** Every contract this service serves, at version 1. */
    public static final List<String> IDS = List.of(
            "verify-connectivity",
            "contrast-catalogues",
            "request-mapping",
            "sync-partners",
            "backfill-prepass",
            "start-backfill",
            "await-activation",
            "activate",
            "fo-verify-connectivity",
            "fo-sync-catalogue",
            "fo-start-backfill",
            "fo-await-activation",
            "fo-activate");

    static TaskRegistration<TaskHandlers.Onboarding, Void> task(String id, TaskHandler<TaskHandlers.Onboarding, Void> handler) {
        return new TaskRegistration<>(id, 1, TOPIC, TaskHandlers.Onboarding.class, Void.class, Reasons.asBefore(handler));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> verifyConnectivityTask(TaskHandlers handlers) {
        return task("verify-connectivity", handlers.onboarding(Integrations::stepVerifyConnectivity));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> contrastCataloguesTask(TaskHandlers handlers) {
        return task("contrast-catalogues", handlers.onboarding(Integrations::stepContrastCatalogues));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> requestMappingTask(TaskHandlers handlers) {
        return task("request-mapping", handlers.onboarding(Integrations::stepRequestMapping));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> syncPartnersTask(TaskHandlers handlers) {
        return task("sync-partners", handlers.onboarding(Integrations::stepSyncPartners));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> backfillPrepassTask(TaskHandlers handlers) {
        return task("backfill-prepass", handlers.onboarding(Integrations::stepBackfillPrePass));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> startBackfillTask(TaskHandlers handlers) {
        return task("start-backfill", handlers.onboarding(Integrations::stepStartBackfill));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> awaitActivationTask(TaskHandlers handlers) {
        return task("await-activation", handlers.onboarding(Integrations::stepAwaitActivation));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> activateTask(TaskHandlers handlers) {
        return task("activate", handlers.onboarding(Integrations::stepActivate));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> foVerifyConnectivityTask(TaskHandlers handlers) {
        return task("fo-verify-connectivity", handlers.frontOffice(FrontOfficeIntegrations::stepVerifyConnectivity));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> foSyncCatalogueTask(TaskHandlers handlers) {
        return task("fo-sync-catalogue", handlers.frontOffice(FrontOfficeIntegrations::stepSyncCatalogue));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> foStartBackfillTask(TaskHandlers handlers) {
        return task("fo-start-backfill", handlers.frontOffice(FrontOfficeIntegrations::stepStartBackfill));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> foAwaitActivationTask(TaskHandlers handlers) {
        return task("fo-await-activation", handlers.frontOffice(FrontOfficeIntegrations::stepAwaitActivation));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Onboarding, Void> foActivateTask(TaskHandlers handlers) {
        return task("fo-activate", handlers.frontOffice(FrontOfficeIntegrations::stepActivate));
    }
}
