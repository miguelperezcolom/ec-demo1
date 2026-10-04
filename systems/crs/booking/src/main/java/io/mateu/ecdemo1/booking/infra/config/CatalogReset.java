package io.mateu.ecdemo1.booking.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.RatePlan;
import io.mateu.ecdemo1.booking.infra.out.catalog.ImportedCatalogs;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * The running catalog back to the one built at start, before any rate plan was opened: the demo's
 * reset (reset-demo) empties catalog_rate_plan, and this is the in-memory half of it — without it the
 * CRS keeps selling e.g. EMPLEADOS-27 until it restarts.
 */
@Component
public class CatalogReset {

    final CrsCatalog catalog;
    final Supplier<CrsCatalog> built;

    @org.springframework.beans.factory.annotation.Autowired
    public CatalogReset(CrsCatalog catalog, ObjectMapper objectMapper) {
        this(catalog, () -> BookingConfig.built(objectMapper));
    }

    CatalogReset(CrsCatalog catalog, Supplier<CrsCatalog> built) {
        this.catalog = catalog;
        this.built = built;
    }

    /** @return the codes no longer sold, as hotel:code */
    public List<String> forgetAddedRatePlans() {
        var before = sold();
        catalog.forgetAddedRatePlans(built.get());
        var after = sold();
        return before.stream().filter(p -> !after.contains(p)).toList();
    }

    private List<String> sold() {
        var chain = catalog.ratePlans().stream().map(p -> "chain:" + p.code());
        var own = catalog.hotels().stream().filter(h -> h.codes() != null)
                .flatMap(h -> h.codes().ratePlans().stream().map(RatePlan::code).map(c -> h.code() + ":" + c));
        return java.util.stream.Stream.concat(chain, own).toList();
    }
}
