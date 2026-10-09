package io.mateu.ecdemo1.loyalty.infra.in.ui.pages;

import io.mateu.ecdemo1.loyalty.store.Accrual;
import io.mateu.ecdemo1.loyalty.store.AccrualRepository;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.fluent.OnLoadTrigger;
import io.mateu.uidl.fluent.Trigger;
import io.mateu.uidl.fluent.TriggersSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Searchable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Acumulaciones: what every closed stay earned every member, newest first. The free text looks in the
 * member, the stay and the hotel. Only a listing: an accrual is not opened, changed or deleted.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Acumulaciones")
public class AccrualsPage implements Listing<AccrualRow>, Searchable, TriggersSupplier {

    final AccrualRepository repository;

    @Override
    public ListingData<AccrualRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request == null ? null : request.searchText();
        var pageable = request == null ? null : request.pageable();
        var size = Math.max(1, pageable == null || pageable.size() <= 0 ? 50 : pageable.size());
        var number = Math.max(0, pageable == null ? 0 : pageable.page());
        var found = repository.findAll(spec(text), PageRequest.of(number, size, Sort.by(Sort.Direction.DESC, "at")));
        var rows = found.getContent().stream().map(Formats::row).toList();
        return new ListingData<>(new Page<>(text, size, number, found.getTotalElements(), rows),
                "Ninguna estancia ha acumulado puntos todavía");
    }

    static Specification<Accrual> spec(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction();
            }
            var like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(cb.like(cb.lower(root.get("memberNumber")), like), cb.like(cb.lower(root.get("stayId")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("hotelCode"), "")), like));
        };
    }

    /**
     * Search as soon as the page loads: a listing that is not navigable does not do it by itself — it
     * stays on its loading skeleton until someone types in the search box.
     */
    @Override
    public List<Trigger> triggers(HttpRequest httpRequest) {
        return List.of(new OnLoadTrigger("search"));
    }
}
