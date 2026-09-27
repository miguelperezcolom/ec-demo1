package io.mateu.ecdemo1.crsintegration.rest;

import io.mateu.ecdemo1.crsintegration.noshow.NoShowReports;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the hotel says a reservation's guests did not arrive, when it wants the answer now — one day
 * Opera's Night Audit (HLA F006). The front office tells it on {@code no-show-reports} instead: an
 * order between services goes by Kafka. The CRS decides what that costs; this only starts «Registrar no-show».
 */
@RestController
@RequiredArgsConstructor
public class NoShowController {

    final NoShowReports reports;

    public record NoShow(String hotelCode, String locator, String reportedBy) {
    }

    public record Answer(String locator, String status) {
    }

    @PostMapping("/no-shows")
    public ResponseEntity<Answer> report(@RequestBody NoShow noShow) {
        var answer = reports.report(noShow.hotelCode(), noShow.locator(), noShow.reportedBy());
        var status = switch (answer) {
            case NOT_IN_THE_CRS -> HttpStatus.NOT_FOUND;
            case ALREADY_CANCELLED -> HttpStatus.CONFLICT;
            case REPORTED, ALREADY_REPORTED -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(new Answer(noShow.locator(), answer.name()));
    }
}
