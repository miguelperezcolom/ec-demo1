package io.mateu.ecdemo1.pmsintegration;

import io.mateu.ecdemo1.pmsintegration.ohip.PmsRejectedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What a PMS_REJECTED cause keeps of Opera's no: its words and its code. */
class PmsRejectedReasonTest {

    @Test
    void operasWordsAndErrorCode() {
        assertThat(new PmsRejectedException(400, "RSV00138", "There are not enough rooms available on Room Type level").reason())
                .isEqualTo("There are not enough rooms available on Room Type level — RSV00138");
    }

    @Test
    void withoutACodeOrWordsNothingReadsNull() {
        assertThat(new PmsRejectedException(400, null, "Opera has no room to suggest for it").reason())
                .isEqualTo("Opera has no room to suggest for it");
        assertThat(new PmsRejectedException(422, "X1", null).reason()).isEqualTo("Opera answered 422 — X1");
    }
}
