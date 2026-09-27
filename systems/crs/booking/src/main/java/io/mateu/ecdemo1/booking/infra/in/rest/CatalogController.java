package io.mateu.ecdemo1.booking.infra.in.rest;

import io.mateu.ecdemo1.booking.application.usecases.catalog.AddRatePlanUseCase;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** The CRS's codes — what the integration's mapping pairs with the PMS's. */
@RestController
@RequiredArgsConstructor
public class CatalogController {

    final CrsCatalog catalog;
    final AddRatePlanUseCase addRatePlan;

    public record NewRatePlan(String code, String name, BigDecimal factor) {
    }

    @GetMapping("/catalog")
    @Operation(summary = "The CRS's codes: hotels and their room types, rate plans, boards, channels, cancellation reasons, payment methods")
    public CrsCatalog catalog() {
        return catalog;
    }

    @PostMapping("/catalog/hotels/{hotelCode}/rate-plans")
    @Operation(summary = "Open a new rate plan in a hotel: sellable at once, and listed in the catalog. "
            + "201 if new, 200 if the hotel already sells it exactly so, 409 if it sells another by that code")
    public ResponseEntity<AddRatePlanUseCase.Result> addRatePlan(@PathVariable String hotelCode,
                                                                 @RequestBody NewRatePlan plan,
                                                                 @RequestParam(defaultValue = "api") String by) {
        var result = addRatePlan.handle(hotelCode, plan.code(), plan.name(), plan.factor(), by);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }
}
