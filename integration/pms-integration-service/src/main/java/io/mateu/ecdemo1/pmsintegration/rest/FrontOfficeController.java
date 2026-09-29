package io.mateu.ecdemo1.pmsintegration.rest;

import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueEntry;
import io.mateu.ecdemo1.integration.model.pms.PmsReservationStamp;
import io.mateu.ecdemo1.pmsintegration.frontoffice.FrontOfficeCatalogue;
import io.mateu.ecdemo1.pmsintegration.frontoffice.OperaStays;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsRejectedException;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsTransientException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * What the pms-fo integration asks of Opera through the connector — queries only: the property's
 * catalogue as a front office reads it, and its reservations of a window with when each was last
 * modified (its backfill and its polling). And the one the front office asks itself, because a screen
 * needs the answer now: which rooms of a type Opera has clean and free, for the desk to pick at the
 * check-in (a query, not an order: orders go by events).
 */
@RestController
@RequestMapping("/front-office")
@RequiredArgsConstructor
public class FrontOfficeController {

    final FrontOfficeCatalogue catalogue;
    final OperaStays stays;
    final io.mateu.ecdemo1.pmsintegration.ohip.OperaFrontDesk desk;

    /**
     * The property's rooms of a type — or the one room given ({@code roomId}: whether the room a stay was
     * given is ready yet) — with Opera's housekeeping and front office status now.
     */
    @GetMapping("/rooms")
    public List<io.mateu.ecdemo1.pmsintegration.ohip.OperaFrontDesk.RoomState> rooms(@RequestParam String hotelId,
                                                                                    @RequestParam(required = false) String roomType,
                                                                                    @RequestParam(required = false) String roomId) {
        return desk.rooms(hotelId, roomType, roomId);
    }

    @GetMapping("/catalogue")
    public List<CatalogueEntry> catalogue(@RequestParam String hotelId) {
        return catalogue.of(hotelId);
    }

    /**
     * The reservations in the house or arriving between {@code from} and {@code to}, cancelled ones
     * included, modified at or after {@code modifiedSince} (ISO local date-time; none: all), oldest
     * modification first.
     */
    @GetMapping("/reservations")
    public List<PmsReservationStamp> reservations(@RequestParam String hotelId,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(defaultValue = "ALL") OperaStays.Scope scope,
                                                  @RequestParam(required = false) String modifiedSince) {
        return stays.window(hotelId, from, to, scope, modifiedSince);
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
