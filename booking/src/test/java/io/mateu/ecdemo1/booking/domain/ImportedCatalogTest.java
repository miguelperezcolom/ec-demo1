package io.mateu.ecdemo1.booking.domain;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MRU01's catalog, imported from its Opera property's (XMAR) by deploy/demo/crs-catalog/generate.py:
 * the CRS's own codes, each with the pair the mapping should find, and the chain's hotels untouched.
 */
class ImportedCatalogTest {

    static final Path PAIRS = Path.of("../deploy/demo/crs-catalog/MRU01-expected-pairs.md");
    static final Pattern PAIR = Pattern.compile("^\\| [^|]+ \\| `([^`]+)` \\| [^|]+ \\| `([^`]+)` \\|");

    final CrsCatalog catalog = CrsCatalog.standard();

    List<String> mru01Codes() {
        var codes = catalog.codes("MRU01");
        return Stream.of(
                catalog.hotel("MRU01").roomTypes().stream().map(CrsCatalog.RoomType::code),
                codes.ratePlans().stream().map(CrsCatalog.RatePlan::code),
                codes.boards().stream().map(CrsCatalog.Board::code),
                codes.channels().stream().map(CrsCatalog.Channel::code),
                codes.paymentMethods().stream().map(CrsCatalog.Code::code),
                codes.cancellationReasons().stream().map(CrsCatalog.Code::code)).flatMap(s -> s).toList();
    }

    @Test
    void mru01HasXmarsRoomTypesAndASelectionOfItsRatesAndBoards() {
        var hotel = catalog.hotel("MRU01");
        var codes = catalog.codes("MRU01");

        assertThat(hotel.name()).isEqualTo("Riu Demo Mauricio");
        assertThat(hotel.currency()).isEqualTo("EUR");
        assertThat(hotel.roomTypes()).hasSize(15)
                .allSatisfy(r -> assertThat(r.basePrice()).isPositive())
                .allSatisfy(r -> assertThat(r.maxOccupancy()).isBetween(2, 5));
        assertThat(codes.ratePlans()).hasSizeBetween(6, 10);
        assertThat(codes.boards()).extracting(CrsCatalog.Board::code)
                .containsExactly("SOLO-ALOJAMIENTO", "DESAYUNO", "COMIDAS", "TODO-INCLUIDO");
        assertThat(codes.channels()).filteredOn(CrsCatalog.Channel::requiresPartner)
                .extracting(CrsCatalog.Channel::code).containsExactly("TTOO", "OTA");
        // The no show the hotel reports cancels with it, in every hotel.
        assertThat(catalog.cancellationReason("MRU01", Booking.NO_SHOW).name()).isEqualTo("No show");
    }

    @Test
    void everyCodeOfMru01HasItsExpectedPairAndNoneIsOperasOwn() throws Exception {
        var pairs = new ArrayList<String[]>();
        for (var line : Files.readAllLines(PAIRS)) {
            var m = PAIR.matcher(line);
            if (m.find()) {
                pairs.add(new String[]{m.group(1), m.group(2)});
            }
        }

        assertThat(pairs.stream().map(p -> p[0]).toList()).containsExactlyInAnyOrderElementsOf(mru01Codes());
        // The mapping has to pair by meaning: no CRS code is the Opera code it maps to (bar the no show,
        // the CRS's everywhere).
        assertThat(pairs).filteredOn(p -> !p[0].equals(Booking.NO_SHOW)).allSatisfy(p -> assertThat(p[0]).isNotEqualTo(p[1]));
    }

    @Test
    void theOtherHotelsKeepTheChainsCodes() {
        assertThat(catalog.hotel("PMI01").codes()).isNull();
        assertThat(catalog.hotel("CUN01").codes()).isNull();
        assertThat(catalog.codes("PMI01").ratePlans()).extracting(CrsCatalog.RatePlan::code)
                .containsExactly("BAR", "NRF", "EB", "TTOO");
        assertThat(catalog.codes("CUN01").boards()).extracting(CrsCatalog.Board::code)
                .containsExactly("SA", "AD", "MP", "PC", "TI");
    }

    @Test
    void aChoiceMadeBeforeTheHotelIsKnownOffersEveryHotelsCodesOnce() {
        var methods = catalog.acrossHotels(CrsCatalog.Codes::paymentMethods, CrsCatalog.Code::code)
                .stream().map(CrsCatalog.Code::code).toList();

        assertThat(methods).contains("AMEX", "EFE", "MASTERCARD", "TRANSFERENCIA").doesNotHaveDuplicates();
    }
}
