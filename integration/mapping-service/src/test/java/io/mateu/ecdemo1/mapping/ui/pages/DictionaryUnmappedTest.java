package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.dictionary.Pending;
import io.mateu.ecdemo1.mapping.dictionary.Pending.PendingCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With no integration chosen the dictionary still lists what is unmapped — every hotel's, each row
 * scoped to its hotel. It listed only the approved entries, and right after an onboarding it looked as
 * if nothing waited for a mapping.
 */
class DictionaryUnmappedTest {

    final Map<String, List<PendingCode>> codes = Map.of(
            "MRU01", List.of(new PendingCode(CodeType.ROOM_TYPE, "STD-KING", "Estándar king", false),
                    new PendingCode(CodeType.BOARD, "AI", "Todo incluido", true)),
            "PMI01", List.of(new PendingCode(CodeType.RATE_PLAN, "RACK", "Rack", false)));

    final Pending pending = new Pending(null, null, null) {
        @Override
        public List<String> hotelsWithPending() {
            return List.of("MRU01", "PMI01");
        }

        @Override
        public List<PendingCode> pendingCodes(String hotelCode) {
            return codes.get(hotelCode);
        }
    };

    final DictionaryCrud crud = new DictionaryCrud(null, null, pending, null, null);

    @Test
    void withNoIntegrationChosenEveryHotelsUnmappedCodesAreListed() {
        assertThat(crud.unmapped(null).map(r -> r.scope() + " " + r.type() + " " + r.crsCode()))
                .containsExactly("MRU01 ROOM_TYPE STD-KING", "PMI01 RATE_PLAN RACK");
    }

    @Test
    void withAnIntegrationChosenOnlyItsHotels() {
        assertThat(crud.unmapped("PMI01").map(EntryRow::crsCode)).containsExactly("RACK");
    }
}
