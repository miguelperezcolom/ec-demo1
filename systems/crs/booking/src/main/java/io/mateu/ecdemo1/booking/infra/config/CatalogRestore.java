package io.mateu.ecdemo1.booking.infra.config;

import io.mateu.ecdemo1.booking.application.usecases.catalog.AddRatePlanUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * The rate plans opened before this start, back in the catalog. A runner, so it happens before the
 * service reports itself ready: no booking or catalog request finds the catalog without them.
 */
@Component
@RequiredArgsConstructor
public class CatalogRestore implements ApplicationRunner {

    final AddRatePlanUseCase addRatePlan;

    @Override
    public void run(ApplicationArguments args) {
        addRatePlan.restore();
    }
}
