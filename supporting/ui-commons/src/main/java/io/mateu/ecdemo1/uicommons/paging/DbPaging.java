package io.mateu.ecdemo1.uicommons.paging;

import io.mateu.uidl.data.Direction;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

/**
 * A listing paged by the DATABASE: the screen's page request becomes a Spring Data
 * {@link Pageable}, the repository answers one page and a count, and the page becomes the
 * listing's {@link ListingData}. Only the rows on screen are ever read.
 *
 * <pre>{@code
 * return DbPaging.page(request, p -> repository.findByNameContainingIgnoreCase(text, p), Rows::of);
 * }</pre>
 *
 * <p>Sorting is opt-in: a grid sorts by its COLUMN names, which are the row's fields, not the
 * entity's properties, and handing Spring Data a property it does not know is an exception, not an
 * unsorted page. So {@link #pageable(io.mateu.uidl.data.Pageable)} ignores the requested order,
 * and {@link #pageable(io.mateu.uidl.data.Pageable, Map)} honours it only for the columns it maps.
 *
 * <p>Kept apart from {@link Paging} so that a service with no Spring Data on its classpath can
 * still use that one.
 */
public final class DbPaging {

    private DbPaging() {
    }

    /** The page the screen asked for, unsorted (the repository's own order). */
    public static Pageable pageable(io.mateu.uidl.data.Pageable pageable) {
        return pageable(pageable, Map.of());
    }

    /** The page the screen asked for, unsorted (the repository's own order). */
    public static Pageable pageable(SearchRequest request) {
        return pageable(request == null ? null : request.pageable());
    }

    /**
     * The page the screen asked for, sorted by the columns in {@code sortable} (grid column →
     * entity property). A column not in it is left out of the order rather than failing the query.
     */
    public static Pageable pageable(io.mateu.uidl.data.Pageable pageable, Map<String, String> sortable) {
        var size = Paging.size(pageable);
        var number = pageable == null ? 0 : Math.max(pageable.page(), 0);
        var orders = new ArrayList<Sort.Order>();
        if (pageable != null && pageable.sort() != null) {
            for (var sort : pageable.sort()) {
                var property = sort == null ? null : sortable.get(sort.field());
                if (property != null) {
                    orders.add(sort.direction() == Direction.descending
                            ? Sort.Order.desc(property) : Sort.Order.asc(property));
                }
            }
        }
        return PageRequest.of(number, size, Sort.by(orders));
    }

    /** A page the repository answered, as the listing's data. */
    public static <E, R> ListingData<R> listing(String searchText, Page<E> page, Function<? super E, ? extends R> toRow) {
        return new ListingData<>(new io.mateu.uidl.data.Page<R>(searchText,
                page.getSize(), page.getNumber(), page.getTotalElements(),
                page.getContent().stream().<R>map(toRow).toList()));
    }

    /**
     * The whole round trip: the request's page, asked of {@code query}, as the listing's data. When
     * the page asked for is past the end — a narrower search after paging forward — it answers the
     * last page instead of an empty grid with rows still counted.
     */
    public static <E, R> ListingData<R> page(SearchRequest request, Function<Pageable, Page<E>> query,
                                             Function<? super E, ? extends R> toRow) {
        var pageable = pageable(request);
        var page = query.apply(pageable);
        if (page.getContent().isEmpty() && pageable.getPageNumber() > 0 && page.getTotalElements() > 0) {
            var last = (int) ((page.getTotalElements() - 1) / pageable.getPageSize());
            page = query.apply(pageable.withPage(last));
        }
        return listing(request == null ? "" : request.searchText(), page, toRow);
    }
}
