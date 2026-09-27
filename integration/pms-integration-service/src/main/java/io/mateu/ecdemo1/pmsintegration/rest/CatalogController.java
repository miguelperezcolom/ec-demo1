package io.mateu.ecdemo1.pmsintegration.rest;

import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.partner.PmsPartner;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaCatalog;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaProfiles;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsRejectedException;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsTransientException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Opera's codes for a property, in the integration's terms — what the mapping pairs the CRS's with. */
@RestController
@RequiredArgsConstructor
public class CatalogController {

    final OperaCatalog catalog;
    final OperaProfiles profiles;

    @GetMapping("/catalog")
    public List<CodeEntry> catalog(@RequestParam(required = false) String hotelId) {
        return catalog.catalog(hotelId);
    }

    /** The chain's partners as Opera has them, read through a property of the chain. */
    @GetMapping("/pms-partners")
    public List<PmsPartner> partners(@RequestParam String hotelId) {
        return profiles.partners(hotelId);
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
