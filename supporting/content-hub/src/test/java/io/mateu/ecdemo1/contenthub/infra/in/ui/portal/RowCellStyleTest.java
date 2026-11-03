package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Redwood draws a row's cell as a column of its grid only if its style has this form: see its content.mjs. */
class RowCellStyleTest {

    @Test
    void a_cell_says_its_share_the_way_redwood_recognises_a_column() {
        assertThat(PageRenderer.cellStyle(8)).matches("flex:\\s*1 1 calc\\(\\d+(\\.\\d+)?%.*").contains("66.67%");
        assertThat(PageRenderer.cellStyle(6)).contains("calc(50.0%");
        assertThat(PageRenderer.cellStyle(12)).contains("calc(100.0%");
    }
}
