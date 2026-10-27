package io.mateu.ecdemo1.pmsintegration.rest;

import io.mateu.ecdemo1.pmsintegration.ohip.OperaAvailability;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsRejectedException;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsTransientException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** What Opera would sell, read-only: a stay's room types, and the free rooms of the next nights. */
@RestController
@RequiredArgsConstructor
public class AvailabilityController {

    final OperaAvailability availability;

    /** The room types Opera would sell {@code quantity} rooms of for the whole stay. */
    @GetMapping("/availability/{hotelId}/stay")
    public Set<String> stay(@PathVariable String hotelId,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrival,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure,
                            @RequestParam(defaultValue = "1") int quantity) {
        return availability.sellable(hotelId, arrival, departure, quantity);
    }

    /** Whether these rooms (a room type once per room) fit in Opera for the stay. */
    @GetMapping("/availability/{hotelId}/fits")
    public boolean fits(@PathVariable String hotelId,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrival,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure,
                        @RequestParam List<String> rooms) {
        return availability.fits(hotelId, arrival, departure, rooms);
    }

    /** The free rooms of each room type, night by night, for the next {@code days} nights (at most 31). */
    @GetMapping("/availability/{hotelId}/calendar")
    public OperaAvailability.Calendar calendar(@PathVariable String hotelId, @RequestParam(defaultValue = "14") int days) {
        return availability.calendar(hotelId, Math.max(1, Math.min(days, 31)));
    }

    @ExceptionHandler(PmsTransientException.class)
    ProblemDetail unavailable(PmsTransientException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(PmsRejectedException.class)
    ProblemDetail refused(PmsRejectedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Opera refused: " + e.getMessage());
    }
}
