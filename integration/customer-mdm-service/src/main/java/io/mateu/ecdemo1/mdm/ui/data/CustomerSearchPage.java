package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.Filterable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import io.mateu.uidl.interfaces.Searchable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Finding a customer: by name, email, phone or document — the free text looks in all of them and in
 * the code. Merged customers are not listed: their code opens the customer they became part of.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Clientes")
public class CustomerSearchPage implements Listing<CustomerSearchRow>, Searchable, Filterable<CustomerFilters>,
        Navigable<CustomerCard, String> {

    static final int PAGE_SIZE = 20;

    final CustomerRepository customers;
    final SourceRepository sources;
    final IdentityResolution resolution;
    final ObjectProvider<CustomerCard> card;

    @Override
    public ListingData<CustomerSearchRow> search(SearchRequest request, HttpRequest httpRequest) {
        var pageable = request == null ? null : request.pageable();
        var page = pageable == null ? 0 : Math.max(pageable.page(), 0);
        var size = pageable == null || pageable.size() <= 0 ? PAGE_SIZE : pageable.size();
        var found = customers.findAll(matching(request == null ? null : request.searchText(),
                request == null ? null : filters(request)), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        var rows = found.getContent().stream().map(this::row).toList();
        return new ListingData<>(new Page<>(request == null ? "" : request.searchText(), size, page,
                found.getTotalElements(), rows), "Ningún cliente coincide");
    }

    CustomerSearchRow row(Customer c) {
        var reservations = (int) sources.findByCustomerIdOrderByFirstSeenAsc(c.id).stream()
                .map(s -> s.hotelCode + "/" + s.locator).distinct().count();
        return new CustomerSearchRow(c.id, c.fullName(), nullToEmpty(c.email), nullToEmpty(c.phone),
                c.documentNumber == null ? "" : (c.documentType == null ? "" : c.documentType + " ") + c.documentNumber,
                reservations, Estados.customer(c.status));
    }

    /** What the free text and the filters ask for, as a query: every word of each must be found. */
    static Specification<Customer> matching(String text, CustomerFilters filters) {
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            where.add(cb.isNull(root.get("aliasOf")));
            if (text != null && !text.isBlank()) {
                for (var word : words(text)) {
                    var like = "%" + word + "%";
                    var any = new ArrayList<Predicate>();
                    for (var field : List.of("id", "firstName", "lastName", "email", "documentNumber", "phone")) {
                        any.add(cb.like(cb.lower(root.<String>get(field)), like));
                    }
                    var digits = word.replaceAll("[^0-9]", "");
                    if (digits.length() >= 3) {
                        any.add(cb.like(digitsOf(root, cb, "phone"), "%" + digits + "%"));
                    }
                    where.add(cb.or(any.toArray(Predicate[]::new)));
                }
            }
            if (filters != null) {
                if (filters.name != null && !filters.name.isBlank()) {
                    var fullName = cb.lower(cb.concat(cb.concat(cb.coalesce(root.<String>get("firstName"), ""), " "),
                            cb.coalesce(root.<String>get("lastName"), "")));
                    for (var word : words(filters.name)) {
                        where.add(cb.like(fullName, "%" + word + "%"));
                    }
                }
                if (filters.email != null && !filters.email.isBlank()) {
                    where.add(cb.like(cb.lower(root.<String>get("email")), "%" + filters.email.trim().toLowerCase(Locale.ROOT) + "%"));
                }
                if (filters.phone != null && !filters.phone.isBlank()) {
                    var digits = filters.phone.replaceAll("[^0-9]", "");
                    where.add(digits.isEmpty()
                            ? cb.like(cb.lower(root.<String>get("phone")), "%" + filters.phone.trim().toLowerCase(Locale.ROOT) + "%")
                            : cb.like(digitsOf(root, cb, "phone"), "%" + digits + "%"));
                }
                if (filters.document != null && !filters.document.isBlank()) {
                    // Documents are compared without spaces, dashes or case: 12345678-z is 12345678Z.
                    var key = filters.document.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
                    where.add(cb.like(cb.lower(cb.function("regexp_replace", String.class, root.<String>get("documentNumber"),
                            cb.literal("[^A-Za-z0-9]"), cb.literal(""), cb.literal("g"))), "%" + key + "%"));
                }
                if (filters.status != null && !filters.status.isEmpty()) {
                    where.add(root.get("status").in(filters.status.stream()
                            .map(s -> CustomerStatus.valueOf(s.name())).collect(Collectors.toSet())));
                }
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** A phone as its digits: +34 600-11 22 33 is 34600112233. */
    static Expression<String> digitsOf(Root<Customer> root, CriteriaBuilder cb, String field) {
        return cb.function("regexp_replace", String.class, root.<String>get(field), cb.literal("[^0-9]"), cb.literal(""),
                cb.literal("g"));
    }

    static List<String> words(String text) {
        return List.of(text.trim().toLowerCase(Locale.ROOT).split("\\s+"));
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Any code the customer ever had opens it: a merged one opens the customer it became part of. */
    @Override
    public CustomerCard view(String id, HttpRequest httpRequest) {
        return card.getObject().load(resolution.survivorOf(id));
    }
}
