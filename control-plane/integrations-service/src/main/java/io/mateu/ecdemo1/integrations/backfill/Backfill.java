package io.mateu.ecdemo1.integrations.backfill;

import io.mateu.ecdemo1.integrations.clients.Services;
import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import io.mateu.ecdemo1.integrations.store.BackfillRun;
import io.mateu.ecdemo1.integrations.store.BackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.IntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Runs the backfills (HLA «Backfill» #10): a batch per tick, nearest arrival first, each reservation
 * projected by the same path a change in the CRS takes — the backfill invokes «Proyectar Reserva»,
 * it does not rewrite it. The throttle is the batch size and the tick.
 *
 * <p>Resumable by construction: the cursor is saved after every batch, and a reservation projected
 * twice is harmless — its process key comes from the reservation and the run, and the write is
 * ordered by version. A half-done backfill is a valid state, and even the desirable one: what is
 * done is what reception needs first.
 *
 * <p>When it is done the property's availability is read in full and its suspension lifted — only
 * then: if it stopped halfway, Opera's availability would still not reflect what is missing (HLA,
 * «restringir pronto, liberar con confirmación»). The PoC has no availability flow, so the read is
 * recorded, not made.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Backfill {

    final BackfillRunRepository runs;
    final IntegrationRepository integrations;
    final Services services;
    final IntegrationsProperties properties;
    final Clock clock;

    @Scheduled(fixedDelayString = "${integrations.backfill-tick:2s}")
    public void tick() {
        for (var run : runs.findByStatus(BackfillRun.Status.RUNNING)) {
            try {
                advance(run);
            } catch (RuntimeException e) {
                log.warn("Backfill {} of {} did not advance: {} — it goes on from where it was on the next tick",
                        run.id, run.crsHotelCode, e.getMessage());
            }
        }
    }

    void advance(BackfillRun run) {
        var page = services.future(run.crsHotelCode, run.cursorArrival, run.cursorLocator, properties.backfillPerTick());
        for (var reservation : page) {
            services.project(run.crsHotelCode, reservation.locator(), "backfill:" + run.id);
            run.cursorArrival = reservation.arrival();
            run.cursorLocator = reservation.locator();
            run.dispatched++;
            if (reservation.arrival().isAfter(run.windowEnd)) {
                run.windowCovered = true;
            }
        }
        run.lastTickAt = clock.instant();
        if (page.size() < properties.backfillPerTick()) {
            run.status = BackfillRun.Status.COMPLETED;
            run.windowCovered = true;
            run.finishedAt = clock.instant();
            runs.save(run);
            integrations.findById(run.integrationId).ifPresent(i -> {
                i.availabilitySuspendedSince = null;
                i.availabilityResyncedAt = clock.instant();
                i.record(clock.instant(), run.onboarding ? "onboarding" : run.startedBy,
                        "Backfill completed: %d reservation(s) projected; availability read in full and its suspension lifted"
                                .formatted(run.dispatched));
                integrations.save(i);
            });
            log.info("Backfill {} of {} completed: {} reservation(s)", run.id, run.crsHotelCode, run.dispatched);
        } else {
            runs.save(run);
        }
    }
}
