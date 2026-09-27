package io.mateu.ecdemo1.booking.application.out.catalog;

import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.RatePlan;

import java.util.List;

/**
 * The rate plans opened while the CRS runs, kept: the catalog the service starts with is the one
 * built in, and these go back on top of it.
 */
public interface AddedRatePlans {

    record Added(String hotelCode, RatePlan ratePlan) {
    }

    void save(String hotelCode, RatePlan ratePlan, String by);

    List<Added> all();
}
