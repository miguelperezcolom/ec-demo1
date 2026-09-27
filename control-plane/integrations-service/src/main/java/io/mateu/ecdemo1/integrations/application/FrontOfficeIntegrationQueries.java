package io.mateu.ecdemo1.integrations.application;

import io.mateu.ecdemo1.integrations.store.FoBackfillRun;
import io.mateu.ecdemo1.integrations.store.FoBackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.FoIntegrationStatus;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegration;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegrationRepository;
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
 * The read side of the pms-fo integrations the console lists and opens, as {@link IntegrationQueries}
 * is the crs-pms ones'. Every change goes through {@link io.mateu.ecdemo1.integrations.frontoffice.FrontOfficeIntegrations}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FrontOfficeIntegrationQueries {

    public static final Sort BY_PROPERTY = Sort.by("pmsHotelCode").and(Sort.by(Sort.Direction.DESC, "createdAt"));

    final FrontOfficeIntegrationRepository integrations;
    final FoBackfillRunRepository runs;

    /** Those whose property, front office, name or status has {@code text} in it, ignoring case. */
    public Page<FrontOfficeIntegration> find(String text, Pageable pageable) {
        return integrations.findAll(matching(text), pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_PROPERTY));
    }

    /** By the PMS property: the one not decommissioned, else the latest. */
    public Optional<FrontOfficeIntegration> byProperty(String pmsHotelCode) {
        return integrations.findFirstByPmsHotelCodeAndStatusNot(pmsHotelCode, FoIntegrationStatus.DECOMMISSIONED)
                .or(() -> integrations.findByPmsHotelCodeOrderByCreatedAtDesc(pmsHotelCode).stream().findFirst());
    }

    public Optional<FoBackfillRun> lastBackfill(String integrationId) {
        return runs.findFirstByIntegrationIdOrderByStartedAtDesc(integrationId);
    }

    static Specification<FrontOfficeIntegration> matching(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction();
            }
            var like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
            var any = new ArrayList<Predicate>();
            for (var field : List.of("pmsHotelCode", "frontOfficeCode", "name")) {
                any.add(cb.like(cb.lower(root.get(field)), like));
            }
            var wanted = text.trim().toLowerCase(Locale.ROOT);
            var statuses = Arrays.stream(FoIntegrationStatus.values())
                    .filter(s -> s.name().toLowerCase(Locale.ROOT).contains(wanted)).toList();
            if (!statuses.isEmpty()) {
                any.add(root.get("status").in(statuses));
            }
            return cb.or(any.toArray(Predicate[]::new));
        };
    }
}
