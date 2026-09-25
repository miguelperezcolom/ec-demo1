package io.mateu.ecdemo1.crsintegration.rest;

import io.mateu.ecdemo1.crsintegration.router.ProcessRouter;
import io.mateu.ecdemo1.crsintegration.source.CrsSource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the hotel says a reservation's guests did not arrive — its front office, or one day Opera's
 * Night Audit (HLA F006). The CRS decides what that costs; this only starts «Registrar no-show».
 */
@RestController
@RequiredArgsConstructor
public class NoShowController {

    final ProcessRouter router;
    final CrsSource crs;

    public record NoShow(String hotelCode, String locator, String reportedBy) {
    }

    public record Answer(String locator, String status) {
    }

    @PostMapping("/no-shows")
    @Transactional
    public ResponseEntity<Answer> report(@RequestBody NoShow noShow) {
        var booking = crs.booking(noShow.locator()).orElse(null);
        if (booking == null || !noShow.hotelCode().equals(booking.hotelCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Answer(noShow.locator(), "NOT_IN_THE_CRS"));
        }
        if ("Cancelled".equals(booking.status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new Answer(noShow.locator(), "ALREADY_CANCELLED"));
        }
        var started = router.noShow(noShow.hotelCode(), noShow.locator(), noShow.reportedBy());
        return ResponseEntity.accepted().body(new Answer(noShow.locator(), started ? "REPORTED" : "ALREADY_REPORTED"));
    }
}
