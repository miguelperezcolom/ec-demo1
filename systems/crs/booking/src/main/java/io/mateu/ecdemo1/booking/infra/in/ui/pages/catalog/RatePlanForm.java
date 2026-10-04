package io.mateu.ecdemo1.booking.infra.in.ui.pages.catalog;

import io.mateu.ecdemo1.booking.application.usecases.catalog.AddRatePlanUseCase;
import io.mateu.ecdemo1.booking.infra.in.ui.suppliers.CatalogLookup;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * A new rate plan for a hotel — what the product team opens in steady state (demo flow 8). Saved
 * through the same use case as the REST API's: sellable at once, and listed in the catalog, so the
 * integration's mapping finds a code it has no equivalence for.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class RatePlanForm {

    final AddRatePlanUseCase addRatePlan;

    @NotEmpty
    @Label("Hotel")
    @Lookup(search = CatalogLookup.class, label = CatalogLookup.class)
    String hotelCode;

    @NotEmpty
    @Label("Code")
    @Help("2 to 20 capital letters, digits, '-' or '_' — e.g. EMPLEADOS-27")
    String code;

    @NotEmpty
    @Label("Name")
    String name;

    @NotNull
    @Label("Factor")
    @Help("On the room's base price: 0.90 is ten percent off, 1 is the public rate (from 0.10 to 3)")
    BigDecimal factor = BigDecimal.ONE;

    RatePlanForm with(String hotelCode) {
        this.hotelCode = hotelCode;
        return this;
    }

    /** @return the new rate plan's id in the listing: hotel/code */
    String create(HttpRequest httpRequest) {
        var by = httpRequest == null ? null : httpRequest.getHeaderValue("X-User-Name");
        var result = addRatePlan.handle(hotelCode, code == null ? null : code.strip(), name, factor,
                by == null || by.isBlank() ? "call center" : by);
        return result.hotelCode() + "/" + result.ratePlan().code();
    }
}
