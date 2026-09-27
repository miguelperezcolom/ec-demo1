package io.mateu.ecdemo1.uicommons.paging;

import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;

import java.util.List;

/**
 * The page a listing's screen asks for, out of rows that are already in memory.
 *
 * <p>This is the FALLBACK, for rows that do not come from a database: a remote API answered with
 * all of them, or they were assembled from several sources. Rows that live in a table are paged
 * by the database instead — see {@link DbPaging} — because paging here means reading every row
 * first, on every page turn.
 */
public final class Paging {

    /** What a screen gets when it does not say how many rows it wants. */
    public static final int DEFAULT_SIZE = 20;

    private Paging() {
    }

    /** The requested page of {@code matching}, and how many there are in all. */
    public static <T> ListingData<T> page(List<T> matching, SearchRequest request) {
        var pageable = request == null ? null : request.pageable();
        var size = size(pageable);
        var number = pageable == null ? 0 : Math.max(pageable.page(), 0);
        if ((long) number * size >= matching.size() && number > 0) {
            number = (matching.size() - 1) / size;   // a narrower search can leave the old page past the end
        }
        var from = Math.min(number * size, matching.size());
        var to = Math.min(from + size, matching.size());
        return new ListingData<>(new Page<>(request == null ? "" : request.searchText(), size, number,
                matching.size(), List.copyOf(matching.subList(from, to))));
    }

    static int size(io.mateu.uidl.data.Pageable pageable) {
        return pageable == null || pageable.size() <= 0 ? DEFAULT_SIZE : pageable.size();
    }
}
