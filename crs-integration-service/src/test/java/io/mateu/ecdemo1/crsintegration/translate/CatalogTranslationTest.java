package io.mateu.ecdemo1.crsintegration.translate;

import io.mateu.ecdemo1.crsintegration.source.CatalogView;
import io.mateu.ecdemo1.crsintegration.source.CatalogView.Code;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** The CRS's catalog as the mapping pairs it: the chain's codes, and a hotel's own ones under its code. */
class CatalogTranslationTest {

    @Test
    void aHotelsOwnCodesCarryTheHotelAndTheChainsCarryNone() {
        var own = new CatalogView.Codes(List.of(new Code("DIRECTA", "Venta directa 2026")),
                List.of(new Code("TODO-INCLUIDO", "Todo incluido")), List.of(new Code("WEB", "Web del hotel")),
                List.of(new Code("NOS", "No show")), List.of(new Code("MASTERCARD", "Tarjeta Mastercard")));
        var catalog = new CatalogView(
                List.of(new CatalogView.Hotel("PMI01", "Riu Demo Palma", List.of(new Code("DBL", "Doble")), null),
                        new CatalogView.Hotel("MRU01", "Riu Demo Mauricio", List.of(new Code("JS-SEA", "Junior suite vista mar")), own)),
                List.of(new Code("BAR", "Tarifa pública")), List.of(new Code("TI", "Todo incluido")),
                List.of(new Code("WEB", "Web propia")), List.of(new Code("NOS", "No show")), List.of(new Code("VISA", "Visa")));

        var entries = CrsTranslator.catalog(catalog);

        assertThat(entries).extracting(CodeEntry::type, CodeEntry::hotelCode, CodeEntry::code).contains(
                tuple(CodeType.ROOM_TYPE, "MRU01", "JS-SEA"),
                tuple(CodeType.RATE_PLAN, null, "BAR"),
                tuple(CodeType.RATE_PLAN, "MRU01", "DIRECTA"),
                tuple(CodeType.BOARD, "MRU01", "TODO-INCLUIDO"),
                tuple(CodeType.CHANNEL, null, "WEB"),
                tuple(CodeType.CHANNEL, "MRU01", "WEB"),
                tuple(CodeType.CANCELLATION_REASON, "MRU01", "NOS"),
                tuple(CodeType.PAYMENT_METHOD, "MRU01", "MASTERCARD"));
        assertThat(entries).filteredOn(e -> "PMI01".equals(e.hotelCode())).extracting(CodeEntry::type)
                .containsOnly(CodeType.ROOM_TYPE);
    }
}
