package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.mapping.CauseType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** A cause in the list says why — Opera's words for a rejection — and when, in the consoles' time. */
class CauseRowTest {

    @Test
    void aRejectionShowsOperasReasonAndCode() {
        var cause = Cause.pmsRejectedReservation("MRU01", "CPKH7Y", "upsert-reservation",
                "There are not enough rooms available on Room Type level — RSV00138");
        assertThat(CauseRow.reason(cause.type(), cause.description()))
                .isEqualTo("There are not enough rooms available on Room Type level — RSV00138");
    }

    @Test
    void anyOtherCauseShowsItsDescription() {
        assertThat(CauseRow.reason(CauseType.MISSING_MAPPING, "No equivalence for rate plan EMPLEADOS-27"))
                .isEqualTo("No equivalence for rate plan EMPLEADOS-27");
        assertThat(CauseRow.reason(CauseType.PMS_REJECTED, null)).isEmpty();
    }

    @Test
    void timesAreLocalNotUtc() {
        var at = Instant.parse("2026-10-04T09:46:24.964254Z");
        assertThat(CausesPage.WHEN.format(at)).isEqualTo("04/10 11:46");
        assertThat(CausesPage.moment(at)).isEqualTo("04/10/2026 11:46:24");
        assertThat(CausesPage.moment(null)).isEmpty();
    }
}
