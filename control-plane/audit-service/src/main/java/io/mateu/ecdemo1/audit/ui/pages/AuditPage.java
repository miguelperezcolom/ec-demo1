package io.mateu.ecdemo1.audit.ui.pages;

import io.mateu.ecdemo1.audit.store.AuditRecord;
import io.mateu.ecdemo1.audit.store.AuditRecordRepository;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.fluent.OnLoadTrigger;
import io.mateu.uidl.fluent.Trigger;
import io.mateu.uidl.fluent.TriggersSupplier;
import io.mateu.uidl.interfaces.Filterable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Searchable;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The audit trail: every auditable action, newest first. The free text looks in every column —
 * parameters and responses included — and the filters narrow by date, hotel, user, action,
 * service and outcome. Only a listing: a record is not opened, edited or deleted.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Audited actions")
public class AuditPage implements Listing<AuditRow>, Searchable, Filterable<AuditFilters>, TriggersSupplier {

    static final int PAGE_SIZE = 50;

    final AuditRecordRepository records;

    @Value("${audit.zone:Europe/Madrid}")
    String zone;

    @Override
    public ListingData<AuditRow> search(SearchRequest request, HttpRequest httpRequest) {
        var zoneId = ZoneId.of(zone);
        var pageable = request.pageable();
        var page = pageable == null ? 0 : Math.max(pageable.page(), 0);
        var size = pageable == null || pageable.size() <= 0 ? PAGE_SIZE : pageable.size();
        var found = records.findAll(matching(request.searchText(), filters(request), zoneId),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "at")));
        var when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(zoneId);
        var rows = found.getContent().stream().map(r -> new AuditRow(when.format(r.at), r.hotelCode, r.actor,
                r.service, r.action, r.succeeded ? new Status(StatusType.SUCCESS, "Carried out")
                        : new Status(StatusType.DANGER, "Refused"), r.parameters, r.response)).toList();
        return new ListingData<>(new Page<>(request.searchText(), size, page, found.getTotalElements(), rows),
                "No audited action matches");
    }

    static Specification<AuditRecord> matching(String text, AuditFilters filters, ZoneId zone) {
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            if (text != null && !text.isBlank()) {
                var like = "%" + text.trim().toLowerCase() + "%";
                where.add(cb.or(List.of("service", "action", "hotelCode", "actor", "parameters", "response").stream()
                        .map(field -> cb.like(cb.lower(root.get(field)), like)).toArray(Predicate[]::new)));
            }
            if (filters != null) {
                contains(filters.hotel, "hotelCode", root, cb, where);
                contains(filters.user, "actor", root, cb, where);
                contains(filters.action, "action", root, cb, where);
                contains(filters.service, "service", root, cb, where);
                if (filters.when != null && filters.when.from() != null) {
                    where.add(cb.greaterThanOrEqualTo(root.get("at"), filters.when.from().atStartOfDay(zone).toInstant()));
                }
                if (filters.when != null && filters.when.to() != null) {
                    where.add(cb.lessThan(root.get("at"), filters.when.to().plusDays(1).atStartOfDay(zone).toInstant()));
                }
                if (filters.outcome != null && filters.outcome.size() == 1) {
                    where.add(cb.equal(root.get("succeeded"), filters.outcome.contains(AuditFilters.Outcome.CARRIED_OUT)));
                }
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    static void contains(String value, String field, jakarta.persistence.criteria.Root<AuditRecord> root,
                         jakarta.persistence.criteria.CriteriaBuilder cb, List<Predicate> where) {
        if (value != null && !value.isBlank()) {
            where.add(cb.like(cb.lower(root.get(field)), "%" + value.trim().toLowerCase() + "%"));
        }
    }

    /** Not navigable, so it would not search on opening by itself. */
    @Override
    public List<Trigger> triggers(HttpRequest httpRequest) {
        return List.of(new OnLoadTrigger("search"));
    }
}
