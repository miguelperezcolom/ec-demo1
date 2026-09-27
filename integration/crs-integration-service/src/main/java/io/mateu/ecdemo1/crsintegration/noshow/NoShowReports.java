package io.mateu.ecdemo1.crsintegration.noshow;

import io.mateu.ecdemo1.crsintegration.inbox.Inbox;
import io.mateu.ecdemo1.crsintegration.router.ProcessRouter;
import io.mateu.ecdemo1.crsintegration.source.CrsSource;
import io.mateu.ecdemo1.integration.model.command.ReportNoShow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A hotel says a reservation's guests did not arrive (HLA F006) — its front office through its outbox
 * ({@code no-show-reports}), or anyone over {@code POST /no-shows}. The CRS decides what that costs;
 * this only starts «Registrar no-show», once per reservation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NoShowReports {

    static final String CONSUMER = "no-show-reports";

    public enum Answer { REPORTED, ALREADY_REPORTED, NOT_IN_THE_CRS, ALREADY_CANCELLED }

    final ProcessRouter router;
    final CrsSource crs;
    final Inbox inbox;

    @Transactional
    public Answer report(String hotelCode, String locator, String reportedBy) {
        var booking = crs.booking(locator).orElse(null);
        if (booking == null || !hotelCode.equals(booking.hotelCode())) {
            return Answer.NOT_IN_THE_CRS;
        }
        if ("Cancelled".equals(booking.status())) {
            return Answer.ALREADY_CANCELLED;
        }
        return router.noShow(hotelCode, locator, reportedBy) ? Answer.REPORTED : Answer.ALREADY_REPORTED;
    }

    /**
     * The command, taken once by its id. What the CRS cannot take — a reservation it does not have, one
     * already cancelled — would be refused again: it is logged, and that is all.
     *
     * @return null for a command already taken
     */
    @Transactional
    public Answer handle(ReportNoShow command) {
        if (command.commandId() == null || command.hotelCode() == null || command.locator() == null) {
            throw new IllegalArgumentException("An incomplete no-show report: " + command);
        }
        if (!inbox.firstTime(CONSUMER, command.commandId())) {
            log.debug("Already taken: {}", command.commandId());
            return null;
        }
        var answer = report(command.hotelCode(), command.locator(), command.reportedBy());
        if (answer == Answer.REPORTED || answer == Answer.ALREADY_REPORTED) {
            log.info("{}/{}: no-show reported by {} ({})", command.hotelCode(), command.locator(), command.reportedBy(), answer);
        } else {
            log.warn("{}/{}: no-show report by {} not taken: {}", command.hotelCode(), command.locator(), command.reportedBy(), answer);
        }
        return answer;
    }
}
