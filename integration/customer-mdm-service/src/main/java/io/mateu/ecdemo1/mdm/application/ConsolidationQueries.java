package io.mateu.ecdemo1.mdm.application;

import io.mateu.ecdemo1.mdm.store.Consolidation;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
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
import java.util.Locale;
import java.util.Set;

/**
 * The read side of what Salesforce's cleaning concluded: a page of what a search keeps, newest first
 * unless the page asks for another order.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsolidationQueries {

    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "receivedAt");

    /** Where a consolidation stands with the reservations — what the listing shows as its propagation. */
    public enum Propagation {
        /** Every reservation it changed was projected again. */
        PROPAGATED,
        /** Some reservation still carries the absorbed code. */
        PENDING,
        /** The contact was deleted, not merged: there is no survivor to carry. */
        REMOVED
    }

    /**
     * A text found in either code or the detail, and the channels ({@code via}) and propagation
     * states to keep. Null or empty keeps everything.
     */
    public record Search(String text, Set<String> via, Set<Propagation> propagation) {

        public static Search all() {
            return new Search(null, null, null);
        }
    }

    final ConsolidationRepository consolidations;

    public Page<Consolidation> page(Search search, Pageable pageable) {
        return consolidations.findAll(matching(search == null ? Search.all() : search),
                pageable.getSort().isSorted() ? pageable
                        : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
    }

    static Specification<Consolidation> matching(Search s) {
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            if (s.text() != null && !s.text().isBlank()) {
                var like = "%" + s.text().trim().toLowerCase(Locale.ROOT) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.<String>get("absorbedId")), like),
                        cb.like(cb.lower(root.<String>get("survivorId")), like),
                        cb.like(cb.lower(root.<String>get("detail")), like)));
            }
            if (s.via() != null && !s.via().isEmpty()) {
                where.add(root.get("via").in(s.via()));
            }
            if (s.propagation() != null && !s.propagation().isEmpty()) {
                var any = new ArrayList<Predicate>();
                for (var p : s.propagation()) {
                    // The same three cases, in the same order, as the listing's badge.
                    any.add(switch (p) {
                        case REMOVED -> cb.isNull(root.get("survivorId"));
                        case PROPAGATED -> cb.and(cb.isNotNull(root.get("survivorId")), cb.isNotNull(root.get("propagatedAt")));
                        case PENDING -> cb.and(cb.isNotNull(root.get("survivorId")), cb.isNull(root.get("propagatedAt")));
                    });
                }
                where.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
