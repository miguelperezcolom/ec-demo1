package io.mateu.ecdemo1.uicommons.paging;

import io.mateu.uidl.data.Direction;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Sort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PagingTest {

    static final List<Integer> ROWS = IntStream.range(0, 45).boxed().toList();

    static SearchRequest request(int page, int size, Sort... sort) {
        return new SearchRequest("x", null, List.of(), new Pageable(page, size, List.of(sort)));
    }

    @Test
    void in_memory_answers_the_page_asked_for_and_the_total() {
        var page = Paging.page(ROWS, request(1, 20)).page();
        assertThat(page.content()).first().isEqualTo(20);
        assertThat(page.content()).hasSize(20);
        assertThat(page.totalElements()).isEqualTo(45);
    }

    @Test
    void in_memory_past_the_end_answers_the_last_page_and_no_size_is_twenty() {
        assertThat(Paging.page(ROWS, request(9, 20)).page().pageNumber()).isEqualTo(2);
        assertThat(Paging.page(ROWS, null).page().content()).hasSize(Paging.DEFAULT_SIZE);
    }

    @Test
    void the_database_is_asked_for_one_page_only() {
        var asked = new ArrayList<org.springframework.data.domain.Pageable>();
        var listing = DbPaging.page(request(1, 10), p -> {
            asked.add(p);
            var from = (int) p.getOffset();
            return new PageImpl<>(ROWS.subList(from, Math.min(from + p.getPageSize(), ROWS.size())), p, ROWS.size());
        }, i -> "row " + i);
        assertThat(asked).singleElement().satisfies(p -> {
            assertThat(p.getPageNumber()).isEqualTo(1);
            assertThat(p.getPageSize()).isEqualTo(10);
            assertThat(p.getSort().isUnsorted()).isTrue();
        });
        assertThat(listing.page().content()).first().isEqualTo("row 10");
        assertThat(listing.page().totalElements()).isEqualTo(45);
        assertThat(listing.page().searchSignature()).isEqualTo("x");
    }

    @Test
    void the_database_past_the_end_answers_the_last_page() {
        var listing = DbPaging.page(request(9, 20), p -> {
            var from = (int) p.getOffset();
            return new PageImpl<>(from >= ROWS.size() ? List.of() : ROWS.subList(from, Math.min(from + 20, ROWS.size())), p, ROWS.size());
        }, i -> i);
        assertThat(listing.page().pageNumber()).isEqualTo(2);
        assertThat(listing.page().content()).containsExactly(40, 41, 42, 43, 44);
    }

    @Test
    void only_mapped_columns_sort() {
        var pageable = DbPaging.pageable(request(0, 20,
                new Sort("name", Direction.descending), new Sort("status", Direction.ascending)).pageable(),
                Map.of("name", "fullName"));
        assertThat(pageable.getSort().toList()).singleElement().satisfies(o -> {
            assertThat(o.getProperty()).isEqualTo("fullName");
            assertThat(o.isDescending()).isTrue();
        });
    }
}
