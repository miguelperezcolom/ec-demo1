package io.mateu.ecdemo1.mdm.application;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.Source;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import io.mateu.ecdemo1.mdm.store.Xref;
import io.mateu.ecdemo1.mdm.store.XrefRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
import java.util.Set;

/**
 * The read side of the customers, for the screens that list and open them: a page of those a search
 * matches — merged ones left out, they are aliases — asked of the database, most recently changed
 * first unless the page asks for another order; and what a customer's page shows of one.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerQueries {

    public static final Sort RECENT_FIRST = Sort.by(Sort.Direction.DESC, "updatedAt");

    /** The columns every word of the free text is looked for in. */
    static final List<String> SEARCHED = List.of("id", "firstName", "lastName", "email", "documentNumber", "phone");

    final CustomerRepository customers;
    final SourceRepository sources;
    final XrefRepository xrefs;
    final ChangeRequestRepository changeRequests;

    public Page<Customer> search(CustomerSearch search, Pageable pageable) {
        return customers.findAll(matching(search == null ? CustomerSearch.text(null) : search), ordered(pageable));
    }

    /**
     * The golden records whose code, full name, email or document has {@code text} in it — the text
     * whole, ignoring case.
     */
    public Page<Customer> goldenRecords(String text, Pageable pageable) {
        return goldenRecords(text, null, null, pageable);
    }

    /** The same, kept to the Salesforce states and statuses asked for; null or empty keeps every one. */
    public Page<Customer> goldenRecords(String text, Set<SalesforceState> salesforce, Set<CustomerStatus> statuses,
                                        Pageable pageable) {
        var spec = containing(text);
        if (salesforce != null && !salesforce.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("salesforceState").in(salesforce));
        }
        if (statuses != null && !statuses.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("status").in(statuses));
        }
        return customers.findAll(spec, ordered(pageable));
    }

    public Optional<Customer> find(String id) {
        return customers.findById(id);
    }

    /** The codes merged into it. */
    public List<Customer> aliasesOf(String id) {
        return customers.findByAliasOf(id);
    }

    /** The reservation passengers it was recognised in, first seen first. */
    public List<Source> sourcesOf(String id) {
        return sources.findByCustomerIdOrderByFirstSeenAsc(id);
    }

    public long countSources(String id) {
        return sources.countByCustomerId(id);
    }

    /** How many reservations it is in: passengers of the same reservation are one. */
    public int reservationsOf(String id) {
        return (int) sourcesOf(id).stream().map(s -> s.hotelCode + "/" + s.locator).distinct().count();
    }

    /** Where a code of it is known in other systems. */
    public List<Xref> xrefsOf(String code) {
        return xrefs.findByCustomerIdOrderBySystemAscReferenceAsc(code);
    }

    /** The changes asked for any of its codes, newest first. */
    public List<ChangeRequest> changeRequestsOf(Collection<String> codes) {
        return changeRequests.findByCustomerIdInOrderByRequestedAtDesc(codes);
    }

    /** A customer as a line names it: its name and its code; the code alone when there is no such customer. */
    public String label(String id) {
        return customers.findById(id).map(c -> c.fullName() + " · " + c.id).orElse(id);
    }

    static Pageable ordered(Pageable pageable) {
        return pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), RECENT_FIRST);
    }

    /** What the free text and the filters ask for, as a query: every word of each must be found. */
    static Specification<Customer> matching(CustomerSearch s) {
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            where.add(cb.isNull(root.get("aliasOf")));
            if (s.text() != null && !s.text().isBlank()) {
                for (var word : words(s.text())) {
                    var like = "%" + word + "%";
                    var any = new ArrayList<Predicate>();
                    for (var field : SEARCHED) {
                        any.add(cb.like(cb.lower(root.<String>get(field)), like));
                    }
                    var digits = word.replaceAll("[^0-9]", "");
                    if (digits.length() >= 3) {
                        any.add(cb.like(digitsOf(root, cb, "phone"), "%" + digits + "%"));
                    }
                    where.add(cb.or(any.toArray(Predicate[]::new)));
                }
            }
            if (s.name() != null && !s.name().isBlank()) {
                for (var word : words(s.name())) {
                    where.add(cb.like(fullName(root, cb), "%" + word + "%"));
                }
            }
            if (s.email() != null && !s.email().isBlank()) {
                where.add(cb.like(cb.lower(root.<String>get("email")), "%" + s.email().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (s.phone() != null && !s.phone().isBlank()) {
                var digits = s.phone().replaceAll("[^0-9]", "");
                where.add(digits.isEmpty()
                        ? cb.like(cb.lower(root.<String>get("phone")), "%" + s.phone().trim().toLowerCase(Locale.ROOT) + "%")
                        : cb.like(digitsOf(root, cb, "phone"), "%" + digits + "%"));
            }
            if (s.document() != null && !s.document().isBlank()) {
                // Documents are compared without spaces, dashes or case: 12345678-z is 12345678Z.
                where.add(cb.like(cb.lower(cb.function("regexp_replace", String.class, root.<String>get("documentNumber"),
                        cb.literal("[^A-Za-z0-9]"), cb.literal(""), cb.literal("g"))), "%" + documentKey(s.document()) + "%"));
            }
            if (s.statuses() != null && !s.statuses().isEmpty()) {
                where.add(root.get("status").in(s.statuses()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** Not an alias, and the text in its code, full name, email or document. */
    static Specification<Customer> containing(String text) {
        return (root, query, cb) -> {
            var notAlias = cb.isNull(root.get("aliasOf"));
            if (text == null || text.isBlank()) {
                return notAlias;
            }
            var like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.and(notAlias, cb.or(
                    cb.like(cb.lower(root.<String>get("id")), like),
                    cb.like(fullName(root, cb), like),
                    cb.like(cb.lower(root.<String>get("email")), like),
                    cb.like(cb.lower(root.<String>get("documentNumber")), like)));
        };
    }

    /** "first last", lower-cased; a missing half is empty. */
    static Expression<String> fullName(Root<Customer> root, CriteriaBuilder cb) {
        return cb.lower(cb.concat(cb.concat(cb.coalesce(root.<String>get("firstName"), ""), " "),
                cb.coalesce(root.<String>get("lastName"), "")));
    }

    /** A phone as its digits: +34 600-11 22 33 is 34600112233. */
    static Expression<String> digitsOf(Root<Customer> root, CriteriaBuilder cb, String field) {
        return cb.function("regexp_replace", String.class, root.<String>get(field), cb.literal("[^0-9]"), cb.literal(""),
                cb.literal("g"));
    }

    static String documentKey(String document) {
        return document.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    static List<String> words(String text) {
        return List.of(text.trim().toLowerCase(Locale.ROOT).split("\\s+"));
    }
}
