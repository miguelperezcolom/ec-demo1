package io.mateu.ecdemo1.integrations.frontoffice;

import io.mateu.ecdemo1.integration.model.pms.PmsReservationChanged;
import io.mateu.ecdemo1.integrations.lifecycle.Writes;
import io.mateu.ecdemo1.integrations.store.FoIntegrationStatus;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The connector wrote a reservation into the PMS: if the property feeds a front office and its
 * integration is active, the reservation is projected into it — read again from the PMS, as it holds
 * it now. A paused or onboarding one lets it go: the polling after resuming, or the backfill, brings it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PmsReservationEvents {

    final FrontOfficeIntegrationRepository integrations;
    final StayProjections projections;
    final Writes writes;

    /** Whether a process was started for it. */
    public boolean on(PmsReservationChanged event) {
        var integration = integrations.findFirstByPmsHotelCodeAndStatusNot(event.pmsHotelCode(), FoIntegrationStatus.DECOMMISSIONED)
                .filter(i -> i.is(FoIntegrationStatus.ACTIVE));
        if (integration.isEmpty()) {
            log.debug("{} {} written: no active front office integration for the property", event.pmsHotelCode(),
                    event.pmsReservationId());
            return false;
        }
        var i = integration.get();
        writes.write(() -> projections.project(i.id, event.pmsHotelCode(), event.pmsReservationId(), "evt-" + event.eventId(),
                "pms-write:" + event.origin()));
        return true;
    }
}
