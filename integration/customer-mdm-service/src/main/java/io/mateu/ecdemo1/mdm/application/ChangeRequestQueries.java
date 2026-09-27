package io.mateu.ecdemo1.mdm.application;

import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The read side of the change requests: a page of those a search matches, asked of the database,
 * newest first unless the page asks for another order; and one of them.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChangeRequestQueries {

    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "requestedAt");

    /** The columns of the request itself the text is looked for in. */
    static final List<String> SEARCHED = List.of("id", "customerId", "origin", "changes", "sendError");

    final ChangeRequestRepository requests;

    /**
     * Those in any of {@code statuses} (none is any) with {@code text} — whole, ignoring case — in their
     * code, their customer's code or full name, where they came from, their changes or why they have
     * not reached Salesforce yet.
     */
    public Page<ChangeRequest> find(String text, Collection<ChangeRequest.Status> statuses, Pageable pageable) {
        return requests.findAll(matching(text, statuses), pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
    }

    public Optional<ChangeRequest> find(String id) {
        return requests.findById(id);
    }

    static Specification<ChangeRequest> matching(String text, Collection<ChangeRequest.Status> statuses) {
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            if (statuses != null && !statuses.isEmpty()) {
                // kept as the status's name: the column is a plain string
                where.add(root.get("status").in(statuses.stream().map(Enum::name).toList()));
            }
            if (text != null && !text.isBlank()) {
                var like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
                var any = new ArrayList<Predicate>();
                for (var field : SEARCHED) {
                    any.add(cb.like(cb.lower(root.<String>get(field)), like));
                }
                // the customer's name lives with the customer: a subquery, not a join to a relation there is not
                var named = query.subquery(String.class);
                var customer = named.from(Customer.class);
                named.select(customer.get("id")).where(cb.like(CustomerQueries.fullName(customer, cb), like));
                any.add(root.get("customerId").in(named));
                where.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
