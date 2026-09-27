package io.mateu.ecdemo1.iacp.infra.in.scheduling;

import io.mateu.ecdemo1.iacp.application.usecases.gitops.ReconcileCatalogueUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two clock-driven reconciles, both off the webhook's path.
 *
 * <p>The startup sync converges a fresh or restarted pod on the repo once it is ready — so a pod
 * that missed the pushes it was down for catches up on boot, without waiting for the next one.
 *
 * <p>The poll is the deliberate opposite of eager, and off by default. Its whole hazard is that it
 * re-asserts the repo on a timer, which would undo a console edit made to a git-managed entry before
 * the next real push — exactly the quick-fix the console is for. So it exists for deployments that
 * would rather have drift corrected than edits preserved, and stays out of the way otherwise. The
 * {@code @Scheduled} fires regardless; the flag is what decides whether it does anything.
 */
@Component
@ConditionalOnProperty(name = "cp.gitops.enabled", havingValue = "true")
@Slf4j
public class GitOpsScheduler {

    private final ReconcileCatalogueUseCase reconcile;
    private final boolean syncOnStartup;
    private final boolean pollEnabled;

    public GitOpsScheduler(ReconcileCatalogueUseCase reconcile,
                           @Value("${cp.gitops.sync-on-startup:true}") boolean syncOnStartup,
                           @Value("${cp.gitops.poll-enabled:false}") boolean pollEnabled) {
        this.reconcile = reconcile;
        this.syncOnStartup = syncOnStartup;
        this.pollEnabled = pollEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (syncOnStartup) {
            reconcile.reconcile("startup");
        } else {
            log.info("GitOps startup sync disabled — waiting for a webhook.");
        }
    }

    @Scheduled(initialDelayString = "${cp.gitops.poll-interval-ms:300000}",
            fixedDelayString = "${cp.gitops.poll-interval-ms:300000}")
    public void poll() {
        if (pollEnabled) {
            reconcile.reconcile("poll");
        }
    }
}
