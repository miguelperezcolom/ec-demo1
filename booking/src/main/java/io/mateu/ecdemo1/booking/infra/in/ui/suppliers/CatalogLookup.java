package io.mateu.ecdemo1.booking.infra.in.ui.suppliers;

import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupLabelSupplier;
import io.mateu.uidl.interfaces.LookupOptionsSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Options and labels for every field that takes a code from the CRS catalog, told apart by the
 * field's name.
 *
 * <p>Codes are offered across all hotels at once — room types, and the rate plans, boards, channels,
 * reasons and methods of a hotel that has its own (MRU01's, imported from its Opera property's): the
 * lookup does not know which hotel the form has picked. Whether the hotel actually has the code is
 * checked when the booking is saved.
 */
@Service
@RequiredArgsConstructor
public class CatalogLookup implements LookupOptionsSupplier, LookupLabelSupplier {

    /**
     * Not offered as a reason to cancel: a no show is the CRS's cancellation, when the hotel reports
     * one. It still names itself on a booking cancelled that way.
     */
    static final String NO_SHOW = "NOS";

    final CrsCatalog catalog;

    @Override
    public ListingData<Option> search(String fieldId, String searchText, Pageable pageable,
                                      HttpRequest httpRequest) {
        var text = searchText == null ? "" : searchText.toLowerCase();
        var matching = options(fieldId).entrySet().stream()
                .filter(e -> !(fieldId.endsWith("cancellationReasonCode") && NO_SHOW.equals(e.getKey())))
                .filter(e -> (e.getKey() + " " + e.getValue()).toLowerCase().contains(text))
                .map(e -> new Option(e.getKey(), e.getKey() + " — " + e.getValue()))
                .toList();
        return new ListingData<>(new Page<>(searchText, matching.size(), 0, matching.size(), matching));
    }

    @Override
    public String label(String fieldName, Object id, HttpRequest httpRequest) {
        if (id == null) {
            return "";
        }
        var name = options(fieldName).get(id.toString());
        return name != null ? id + " — " + name : id.toString();
    }

    private Map<String, String> options(String field) {
        if (field.endsWith("hotelCode")) {
            return map(catalog.hotels(), CrsCatalog.Hotel::code, CrsCatalog.Hotel::name);
        }
        if (field.endsWith("channelCode")) {
            return map(catalog.acrossHotels(CrsCatalog.Codes::channels, CrsCatalog.Channel::code), CrsCatalog.Channel::code, CrsCatalog.Channel::name);
        }
        if (field.endsWith("roomTypeCode")) {
            return map(catalog.hotels().stream().flatMap(h -> h.roomTypes().stream())
                            .collect(Collectors.toMap(CrsCatalog.RoomType::code, r -> r, (a, b) -> a))
                            .values().stream().toList(),
                    CrsCatalog.RoomType::code, CrsCatalog.RoomType::name);
        }
        if (field.endsWith("ratePlanCode")) {
            return map(catalog.acrossHotels(CrsCatalog.Codes::ratePlans, CrsCatalog.RatePlan::code), CrsCatalog.RatePlan::code, CrsCatalog.RatePlan::name);
        }
        if (field.endsWith("boardCode")) {
            return map(catalog.acrossHotels(CrsCatalog.Codes::boards, CrsCatalog.Board::code), CrsCatalog.Board::code, CrsCatalog.Board::name);
        }
        if (field.endsWith("cancellationReasonCode")) {
            return map(catalog.acrossHotels(CrsCatalog.Codes::cancellationReasons, CrsCatalog.Code::code), CrsCatalog.Code::code, CrsCatalog.Code::name);
        }
        if (field.endsWith("methodCode")) {
            return map(catalog.acrossHotels(CrsCatalog.Codes::paymentMethods, CrsCatalog.Code::code), CrsCatalog.Code::code, CrsCatalog.Code::name);
        }
        throw new IllegalArgumentException("No catalog for field " + field);
    }

    private static <T> Map<String, String> map(List<T> items, Function<T, String> code, Function<T, String> name) {
        return items.stream().collect(Collectors.toMap(code, name, (a, b) -> a, java.util.LinkedHashMap::new));
    }
}
