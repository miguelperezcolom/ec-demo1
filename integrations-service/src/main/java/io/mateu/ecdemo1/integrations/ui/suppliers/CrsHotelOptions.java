package io.mateu.ecdemo1.integrations.ui.suppliers;

import io.mateu.ecdemo1.integrations.clients.Services;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupOptionsSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The hotels of the CRS, read from the CRS itself through its adapter rather than typed: a hotel
 * code that is not the CRS's would hold every reservation of a hotel that does not exist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrsHotelOptions implements LookupOptionsSupplier {

    final Services services;

    @Override
    public ListingData<Option> search(String fieldId, String searchText, Pageable pageable, HttpRequest httpRequest) {
        var text = searchText == null ? "" : searchText.toLowerCase();
        List<Option> options = List.of();
        try {
            options = services.crsHotels().stream()
                    .filter(h -> (h.code() + " " + h.description()).toLowerCase().contains(text))
                    .map(h -> new Option(h.code(), h.code() + " · " + h.description()))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("The CRS did not list its hotels: {}", e.getMessage());
        }
        return new ListingData<>(new Page<>(searchText, options.size(), 0, options.size(), options));
    }
}
