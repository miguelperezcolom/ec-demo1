package io.mateu.ecdemo1.integrations.frontoffice;

import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import io.mateu.ecdemo1.integrations.store.FoBackfillRun;
import io.mateu.ecdemo1.integrations.store.FoBackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;

/**
 * Runs the front offices' backfills: a batch per tick of what the run still has pending, each one a
 * «proyectar-estancia» started in the transaction that takes it off the list — dispatched and taken
 * off, or neither. The throttle is the batch size and the tick, as the crs-pms backfill's.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FoBackfill {

    final FoBackfillRunRepository runs;
    final FrontOfficeIntegrationRepository integrations;
    final StayProjections projections;
    final PlatformTransactionManager transactions;
    final IntegrationsProperties properties;
    final Clock clock;

    @Scheduled(fixedDelayString = "${integrations.backfill-tick:2s}")
    public void tick() {
        for (var run : runs.findByStatus(FoBackfillRun.Status.RUNNING)) {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(status -> advance(run.id));
            } catch (RuntimeException e) {
                log.warn("Front office backfill {} of {} did not advance: {} — it goes on on the next tick",
                        run.id, run.pmsHotelCode, e.getMessage());
            }
        }
    }

    void advance(String runId) {
        var run = runs.findById(runId).orElseThrow();
        if (run.status != FoBackfillRun.Status.RUNNING) {
            return;
        }
        var pending = new ArrayList<>(run.pending == null ? java.util.List.<FoBackfillRun.Item>of() : run.pending);
        var batch = pending.subList(0, Math.min(properties.backfillPerTick(), pending.size()));
        for (var item : batch) {
            projections.project(run.integrationId, run.pmsHotelCode, item.pmsReservationId(),
                    item.lastModified() == null ? "backfill-" + run.id : item.lastModified(), "fo-backfill:" + run.id);
            run.dispatched++;
        }
        batch.clear();
        run.pending = pending;
        run.lastTickAt = clock.instant();
        if (pending.isEmpty()) {
            run.status = FoBackfillRun.Status.COMPLETED;
            run.finishedAt = clock.instant();
            integrations.findById(run.integrationId).ifPresent(i -> {
                i.record(clock.instant(), run.onboarding ? "onboarding" : run.startedBy,
                        "Backfill completed: %d reservation(s) projected into the front office".formatted(run.dispatched));
                integrations.save(i);
            });
            log.info("Front office backfill {} of {} completed: {} reservation(s)", run.id, run.pmsHotelCode, run.dispatched);
        }
        runs.save(run);
    }
}
