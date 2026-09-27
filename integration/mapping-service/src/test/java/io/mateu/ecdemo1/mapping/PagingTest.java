package io.mateu.ecdemo1.mapping;

import io.mateu.ecdemo1.mapping.ui.Paging;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.SearchRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** A listing answers with the page its screen asked for, and says how many rows there are in all. */
class PagingTest {

    static final List<Integer> ROWS = IntStream.range(0, 45).boxed().toList();

    static SearchRequest asking(int page, int size) {
        return new SearchRequest("x", null, null, new Pageable(page, size, List.of()));
    }

    @Test
    void thePageAskedForAndTheTotal() {
        var second = Paging.page(ROWS, asking(1, 20)).page();
        assertThat(second.content()).containsExactlyElementsOf(ROWS.subList(20, 40));
        assertThat(second.totalElements()).isEqualTo(45);
        assertThat(second.pageNumber()).isEqualTo(1);
        assertThat(Paging.page(ROWS, asking(2, 20)).page().content()).containsExactly(40, 41, 42, 43, 44);
    }

    @Test
    void withoutAPageAskedForTheFirstOfTheDefaultSize() {
        var page = Paging.page(ROWS, new SearchRequest(null, null, null, null)).page();
        assertThat(page.content()).hasSize(Paging.DEFAULT_SIZE);
        assertThat(page.pageNumber()).isZero();
    }

    @Test
    void aPagePastTheEndFallsBackToTheLastOne() {
        var page = Paging.page(ROWS.subList(0, 5), asking(3, 20)).page();
        assertThat(page.content()).containsExactly(0, 1, 2, 3, 4);
        assertThat(page.pageNumber()).isZero();
        assertThat(Paging.page(List.of(), asking(0, 20)).page().content()).isEmpty();
    }
}
