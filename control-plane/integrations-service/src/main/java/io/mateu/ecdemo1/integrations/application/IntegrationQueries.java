package io.mateu.ecdemo1.integrations.application;

import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integrations.store.BackfillRun;
import io.mateu.ecdemo1.integrations.store.BackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.Integration;
import io.mateu.ecdemo1.integrations.store.IntegrationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The read side of the integrations the console lists and opens: one page of those a text matches,
 * asked of the database, by CRS hotel unless the page asks for another order; one of them; and its
 * last backfill. Every change goes through {@link io.mateu.ecdemo1.integrations.lifecycle.Integrations}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntegrationQueries {

    public static final Sort BY_HOTEL = Sort.by("crsHotelCode");

    final IntegrationRepository integrations;
    final BackfillRunRepository runs;

    /** Those whose CRS hotel, Opera property, name or status has {@code text} in it, ignoring case. */
    public Page<Integration> find(String text, Pageable pageable) {
        return integrations.findAll(matching(text), pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_HOTEL));
    }

    /** By the CRS hotel: one integration per hotel. */
    public Optional<Integration> byCrsHotel(String crsHotelCode) {
        return integrations.findByCrsHotelCode(crsHotelCode);
    }

    public Optional<BackfillRun> lastBackfill(String integrationId) {
        return runs.findFirstByIntegrationIdOrderByStartedAtDesc(integrationId);
    }

    static Specification<Integration> matching(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction();
            }
            var like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
            var any = new ArrayList<Predicate>();
            for (var field : List.of("crsHotelCode", "pmsHotelCode", "name")) {
                any.add(cb.like(cb.lower(root.get(field)), like));
            }
            // The status is an enum column: which statuses the text names is worked out here, not in SQL.
            var statuses = statusesNamedBy(text);
            if (!statuses.isEmpty()) {
                any.add(root.get("status").in(statuses));
            }
            return cb.or(any.toArray(Predicate[]::new));
        };
    }

    static List<IntegrationStatus> statusesNamedBy(String text) {
        var wanted = text.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(IntegrationStatus.values())
                .filter(s -> s.name().toLowerCase(Locale.ROOT).contains(wanted)).toList();
    }
}
