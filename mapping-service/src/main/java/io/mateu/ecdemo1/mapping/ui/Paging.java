package io.mateu.ecdemo1.mapping.ui;

import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;

import java.util.List;

/**
 * The page a listing's screen asks for, out of every row it matched. The listings here build their
 * rows in memory; handing all of them back as one page is what left the grid without pagination.
 */
public final class Paging {

    public static final int DEFAULT_SIZE = 20;

    private Paging() {
    }

    public static <T> ListingData<T> page(List<T> matching, SearchRequest request) {
        var pageable = request == null ? null : request.pageable();
        var size = pageable == null || pageable.size() <= 0 ? DEFAULT_SIZE : pageable.size();
        var number = pageable == null ? 0 : Math.max(pageable.page(), 0);
        if ((long) number * size >= matching.size() && number > 0) {
            number = (matching.size() - 1) / size;   // a narrower search can leave the old page past the end
        }
        var from = Math.min(number * size, matching.size());
        var to = Math.min(from + size, matching.size());
        return new ListingData<>(new Page<>(request == null ? "" : request.searchText(), size, number,
                matching.size(), List.copyOf(matching.subList(from, to))));
    }
}
