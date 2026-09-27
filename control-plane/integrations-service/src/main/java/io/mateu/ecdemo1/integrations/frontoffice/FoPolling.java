package io.mateu.ecdemo1.integrations.frontoffice;

import io.mateu.ecdemo1.integration.model.pms.PmsReservationStamp;
import io.mateu.ecdemo1.integrations.audit.Audited;
import io.mateu.ecdemo1.integrations.lifecycle.Writes;
import io.mateu.ecdemo1.integrations.store.FoIntegrationStatus;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegration;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * How a change made in the PMS itself reaches the front office: every active pms-fo integration asks
 * the PMS, every so often, for the property's reservations of its window modified since the last one
 * it projected — OHIP's reservation search has no «modified since» filter, so the connector pages the
 * window and keeps those whose last modification is at or after the cursor. Each is projected by
 * «proyectar-estancia» with that modification in its key, and the cursor moves to the latest one, in
 * the same transaction. The cursor itself comes back every time (at or after): the engine drops the
 * process it already has, and a change made in the same second as the last one is not missed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FoPolling {

    final FrontOfficeIntegrationRepository integrations;
    final FrontOfficeIntegrations lifecycle;
    final StayProjections projections;
    final Writes writes;
    final Clock clock;

    @Scheduled(fixedDelayString = "${integrations.front-office.poll:60s}", initialDelayString = "${integrations.front-office.poll:60s}")
    public void pollAll() {
        for (var i : integrations.findByStatus(FoIntegrationStatus.ACTIVE)) {
            try {
                poll(i.id);
            } catch (RuntimeException e) {
                log.warn("Polling the PMS for front office integration {} ({}) failed: {} — next time", i.id, i.pmsHotelCode,
                        e.getMessage());
            }
        }
    }

    /** Asks the PMS now, instead of waiting for the next poll. */
    @Audited("Poll the PMS now")
    public FrontOfficeIntegration pollNow(String id, String by) {
        var i = lifecycle.find(id);
        if (!i.is(FoIntegrationStatus.ACTIVE)) {
            throw new IllegalStateException("Only an active front office integration polls the PMS; this one is " + i.getStatus());
        }
        return poll(id);
    }

    /** One poll: asked outside any transaction, dispatched and the cursor moved in one. */
    public FrontOfficeIntegration poll(String id) {
        var i = lifecycle.find(id);
        var cursor = i.pollCursor;
        var changed = lifecycle.window(i, cursor);
        return writes.write(() -> {
            var x = lifecycle.find(id);
            var fresh = changed.stream().filter(s -> !projectedAlready(s, cursor)).toList();
            for (var stamp : changed) {
                projections.project(x.id, x.pmsHotelCode, stamp.pmsReservationId(), stamp.lastModified(), "fo-poll");
            }
            x.pollCursor = latest(changed, x.pollCursor);
            x.lastPollAt = clock.instant();
            x.lastPollChanges = fresh.size();
            if (!fresh.isEmpty()) {
                x.record(clock.instant(), "polling", "%d reservation(s) changed in the PMS since %s: projected into the front office"
                        .formatted(fresh.size(), cursor == null ? "the start" : cursor));
            }
            return integrations.save(x);
        });
    }

    /** What the cursor already names came back only because the search is «at or after»: not news. */
    static boolean projectedAlready(PmsReservationStamp stamp, String cursor) {
        return cursor != null && cursor.equals(stamp.lastModified());
    }

    static String latest(List<PmsReservationStamp> stamps, String cursor) {
        return java.util.stream.Stream.concat(stamps.stream().map(PmsReservationStamp::lastModified), java.util.stream.Stream.of(cursor))
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }
}
