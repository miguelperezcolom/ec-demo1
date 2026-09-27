package io.mateu.ecdemo1.mapping.queries;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the dictionary's screens read, asked of the database: filtered, ordered and paged there, so
 * only the rows on screen are loaded.
 *
 * <p>The order is the listing's: what waits for someone first (PROPOSED), then what is in force
 * (APPROVED), then the history; within each, by type (in the order {@link CodeType} declares them),
 * CRS code, newest version first — and the id last, so that paging never shows a row twice.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictionaryQueries {

    final MappingEntryRepository entries;

    /**
     * @param hotel    the hotel's own entries and the chain's; null, every entry
     * @param type     null, any
     * @param statuses null, any; empty, none
     * @param text     contained in "TYPE CRS PMS scope", case-insensitive, as the search box always matched
     */
    public record Filter(String hotel, CodeType type, Set<EntryStatus> statuses, String text) {
    }

    public Page<MappingEntry> page(Filter filter, Pageable pageable) {
        return entries.findAll(matching(filter), pageable);
    }

    /** {@code size} rows from {@code offset}: the part of a screen's page that comes after rows read elsewhere. */
    public List<MappingEntry> window(Filter filter, long offset, int size) {
        return entries.findAll(matching(filter), new OffsetPageable(offset, size)).getContent();
    }

    public long count(Filter filter) {
        return entries.count(matching(filter));
    }

    /** Every proposal waiting for a decision that applies to this hotel (its own, and the chain's), of this type. */
    public List<MappingEntry> proposals(String hotel, CodeType type) {
        return entries.findAll(matching(new Filter(hotel, type, Set.of(EntryStatus.PROPOSED), null)));
    }

    public Optional<MappingEntry> entry(String id) {
        return entries.findById(id);
    }

    static Specification<MappingEntry> matching(Filter filter) {
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            if (filter.hotel() != null) {
                where.add(cb.or(cb.isNull(root.get("hotelCode")), cb.equal(root.get("hotelCode"), filter.hotel())));
            }
            if (filter.type() != null) {
                where.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.statuses() != null) {
                where.add(filter.statuses().isEmpty() ? cb.disjunction() : root.get("status").in(filter.statuses()));
            }
            var pattern = Like.contains(filter.text());
            if (!"%".equals(pattern)) {
                where.add(cb.like(cb.lower(searchable(root, cb)), pattern, Like.ESCAPE));
            }
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                query.orderBy(cb.asc(statusOrder(root, cb)), cb.asc(typeOrder(root, cb)), cb.asc(root.get("sourceCode")),
                        cb.desc(root.get("entryVersion")), cb.asc(root.get("id")));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** "TYPE CRS PMS scope" — what the search box has always been matched against. */
    static Expression<String> searchable(Root<MappingEntry> root, CriteriaBuilder cb) {
        Expression<String> text = root.get("type").as(String.class);
        text = cb.concat(cb.concat(text, " "), root.get("sourceCode"));
        text = cb.concat(cb.concat(text, " "), cb.coalesce(root.<String>get("targetCode"), ""));
        return cb.concat(cb.concat(text, " "), cb.coalesce(root.<String>get("hotelCode"), "chain"));
    }

    static Expression<Integer> statusOrder(Root<MappingEntry> root, CriteriaBuilder cb) {
        return cb.<Integer>selectCase()
                .when(cb.equal(root.get("status"), EntryStatus.PROPOSED), 0)
                .when(cb.equal(root.get("status"), EntryStatus.APPROVED), 1)
                .otherwise(2);
    }

    static Expression<Integer> typeOrder(Root<MappingEntry> root, CriteriaBuilder cb) {
        var order = cb.<Integer>selectCase();
        for (var type : CodeType.values()) {
            order = order.when(cb.equal(root.get("type"), type), type.ordinal());
        }
        return order.otherwise(CodeType.values().length);
    }
}
