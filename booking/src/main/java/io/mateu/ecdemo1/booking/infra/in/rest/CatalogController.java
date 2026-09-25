package io.mateu.ecdemo1.booking.infra.in.rest;

import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The CRS's codes — what the integration's mapping pairs with the PMS's. */
@RestController
@RequiredArgsConstructor
public class CatalogController {

    final CrsCatalog catalog;

    @GetMapping("/catalog")
    @Operation(summary = "The CRS's codes: hotels and their room types, rate plans, boards, channels, cancellation reasons, payment methods")
    public CrsCatalog catalog() {
        return catalog;
    }
}
