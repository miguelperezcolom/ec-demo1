package io.mateu.ecdemo1.booking.application.usecases.catalog;

import io.mateu.ecdemo1.booking.application.out.catalog.AddedRatePlans;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.RatePlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The product team opens a new rate plan in a hotel. It is sellable at once and the catalog lists it;
 * the integration then finds a code it has no equivalence for — a hotel with an active integration
 * learns of a new code this way, with no one telling it. Adding the same rate plan again changes
 * nothing.
 */
@Service
@RequiredArgsConstructor
public class AddRatePlanUseCase {

    final CrsCatalog catalog;
    final AddedRatePlans added;

    public record Result(String hotelCode, RatePlan ratePlan, boolean created) {
    }

    @Transactional
    public Result handle(String hotelCode, String code, String name, BigDecimal factor, String by) {
        var plan = RatePlan.valid(code, name, factor);
        // Refused before anything is kept if the hotel does not exist or sells another plan by that code.
        catalog.hotel(hotelCode);
        var already = catalog.codes(hotelCode).ratePlans().stream().anyMatch(p -> p.code().equals(plan.code()));
        if (!already) {
            added.save(hotelCode, plan, by);
        }
        var created = catalog.addRatePlan(hotelCode, plan);
        return new Result(hotelCode, catalog.ratePlan(hotelCode, plan.code()), created);
    }

    /** Puts back, on the catalog built in, the rate plans opened since. */
    public void restore() {
        added.all().forEach(a -> {
            try {
                catalog.addRatePlan(a.hotelCode(), a.ratePlan());
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(AddRatePlanUseCase.class)
                        .warn("Rate plan {} of {} not restored: {}", a.ratePlan().code(), a.hotelCode(), e.getMessage());
            }
        });
    }
}
