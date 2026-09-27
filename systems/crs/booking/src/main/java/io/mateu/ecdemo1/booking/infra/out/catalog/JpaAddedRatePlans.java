package io.mateu.ecdemo1.booking.infra.out.catalog;

import io.mateu.ecdemo1.booking.application.out.catalog.AddedRatePlans;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.RatePlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JpaAddedRatePlans implements AddedRatePlans {

    final AddedRatePlanRepository repository;
    final Clock clock;

    @Override
    public void save(String hotelCode, RatePlan ratePlan, String by) {
        repository.save(new AddedRatePlanEntity(hotelCode, ratePlan.code(), ratePlan.name(), ratePlan.factor(),
                clock.instant(), by));
    }

    @Override
    public List<Added> all() {
        return repository.findAll().stream().sorted(Comparator.comparing(e -> e.addedAt))
                .map(e -> new Added(e.hotelCode, new RatePlan(e.code, e.name, e.factor))).toList();
    }
}
