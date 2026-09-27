package io.mateu.ecdemo1.crsintegration.rest;

import io.mateu.ecdemo1.crsintegration.source.CrsSource;
import io.mateu.ecdemo1.crsintegration.translate.CrsTranslator;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * The CRS side in the integration's terms, read fresh on every call. It is how the steps of a
 * process get the data they act on — the process itself carries only references, so a process
 * that waited two days acts on the reservation as it is now, not as it was then.
 */
@RestController
@RequiredArgsConstructor
public class CanonicalController {

    final CrsSource crs;

    @GetMapping("/reservations/{hotelCode}/{locator}")
    public Reservation reservation(@PathVariable String hotelCode, @PathVariable String locator) {
        return crs.booking(locator)
                .filter(b -> b.hotelCode().equals(hotelCode))
                .map(CrsTranslator::reservation)
                .orElseThrow(() -> new NoSuchElementException(
                        "Reservation %s of hotel %s not found in the CRS".formatted(locator, hotelCode)));
    }

    @GetMapping("/partners/{code}")
    public Partner partner(@PathVariable String code) {
        return crs.partner(code).map(CrsTranslator::partner)
                .orElseThrow(() -> new NoSuchElementException("Partner %s not found".formatted(code)));
    }

    @GetMapping("/catalog")
    public List<CodeEntry> catalog() {
        return CrsTranslator.catalog(crs.catalog());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
