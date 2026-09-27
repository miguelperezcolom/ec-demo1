package io.mateu.ecdemo1.booking.infra.out.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.Board;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.Channel;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.Code;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.Codes;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.Hotel;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.RatePlan;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.RoomType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The catalogs of the hotels imported from their PMS's, read from the classpath: the file is
 * versioned next to the classes, and the CRS never calls Opera.
 */
public class ImportedCatalogs {

    /** Where a hotel's imported catalog lives. */
    static final String IMPORTED = "/crs-catalog/%s.json";

    final ObjectMapper objectMapper;

    public ImportedCatalogs(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** The chain's catalog, with the imported hotels read by a plain mapper — for tests and tools. */
    public static CrsCatalog standardCatalog() {
        return CrsCatalog.standard(new ImportedCatalogs(new ObjectMapper())::hotel);
    }

    public Hotel hotel(String hotelCode) {
        try (var in = ImportedCatalogs.class.getResourceAsStream(IMPORTED.formatted(hotelCode))) {
            if (in == null) {
                throw new IllegalStateException("No imported catalog for " + hotelCode);
            }
            var file = objectMapper.readValue(in, ImportedHotel.class);
            return new Hotel(file.hotel().code(), file.hotel().name(), file.hotel().currency(), file.roomTypes(),
                    new Codes(file.ratePlans(), file.boards(), file.channels(), file.cancellationReasons(),
                            file.paymentMethods()));
        } catch (IOException e) {
            throw new UncheckedIOException("Unreadable imported catalog for " + hotelCode, e);
        }
    }

    record ImportedHotel(HotelHeader hotel, List<RoomType> roomTypes, List<RatePlan> ratePlans, List<Board> boards,
                         List<Channel> channels, List<Code> cancellationReasons, List<Code> paymentMethods) {
    }

    record HotelHeader(String code, String name, String currency) {
    }
}
