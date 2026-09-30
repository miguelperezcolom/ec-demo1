package io.mateu.ecdemo1.audit.application;

import io.mateu.ecdemo1.audit.store.AuditRecord;
import io.mateu.ecdemo1.audit.store.AuditRecordRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The read side of the trail: one page of the records a search matches, asked of the database —
 * filters, order and page alike. Newest first unless the page asks for another order.
 */
@Service
@Transactional(readOnly = true)
public class AuditQueries {

    /** The order of the trail when nobody asked for one. */
    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "at");

    /** The columns the free text looks in. */
    static final List<String> SEARCHED = List.of("service", "action", "hotelCode", "actor", "parameters", "response");

    final AuditRecordRepository records;
    final ZoneId zone;

    public AuditQueries(AuditRecordRepository records, @Value("${audit.zone:Europe/Madrid}") String zone) {
        this.records = records;
        this.zone = ZoneId.of(zone);
    }

    /**
     * A reservation's history: what was done on the stay, or on the CRS's booking of that locator, newest
     * first — by its columns, or, for an action audited before they were read, by its parameters.
     */
    public List<AuditRecord> ofReservation(String stayId, String locator, int limit) {
        if (blank(stayId) && blank(locator)) {
            return List.of();
        }
        Specification<AuditRecord> spec = (root, query, cb) -> {
            var any = new ArrayList<Predicate>();
            for (var pair : List.of(new String[]{"stayId", stayId}, new String[]{"locator", locator})) {
                if (!blank(pair[1])) {
                    any.add(cb.equal(root.get(pair[0]), pair[1]));
                    any.add(cb.and(cb.isNull(root.get(pair[0])),
                            cb.like(root.get("parameters"), "%\"" + pair[0] + "\":\"" + pair[1] + "\"%")));
                }
            }
            return cb.or(any.toArray(Predicate[]::new));
        };
        return records.findAll(spec, PageRequest.of(0, Math.max(1, Math.min(limit, 500)), NEWEST_FIRST)).getContent();
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public Page<AuditRecord> find(AuditQuery query, Pageable pageable) {
        return records.findAll(matching(query == null ? AuditQuery.everything() : query, zone), ordered(pageable));
    }

    /** The page asked for, newest first when it names no order. */
    static Pageable ordered(Pageable pageable) {
        return pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
    }

    static Specification<AuditRecord> matching(AuditQuery q, ZoneId zone) {
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            if (q.text() != null && !q.text().isBlank()) {
                var like = like(q.text());
                where.add(cb.or(SEARCHED.stream().map(field -> cb.like(cb.lower(root.get(field)), like))
                        .toArray(Predicate[]::new)));
            }
            contains(q.hotel(), "hotelCode", root, cb, where);
            contains(q.user(), "actor", root, cb, where);
            contains(q.action(), "action", root, cb, where);
            contains(q.service(), "service", root, cb, where);
            if (q.from() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("at"), q.from().atStartOfDay(zone).toInstant()));
            }
            if (q.to() != null) {
                where.add(cb.lessThan(root.get("at"), q.to().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            if (q.succeeded() != null) {
                where.add(cb.equal(root.get("succeeded"), q.succeeded()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    static void contains(String value, String field, Root<AuditRecord> root, CriteriaBuilder cb, List<Predicate> where) {
        if (value != null && !value.isBlank()) {
            where.add(cb.like(cb.lower(root.get(field)), like(value)));
        }
    }

    static String like(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}
