package io.mateu.ecdemo1.mapping.ui.suppliers;

import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
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
 * The integrations, to choose which one to map: mapping is always for a CRS hotel and the Opera
 * property it is integrated with. The value is the CRS hotel, which is what the mapping is keyed by.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationOptions implements LookupOptionsSupplier {

    final IntegrationClients clients;

    @Override
    public ListingData<Option> search(String fieldId, String searchText, Pageable pageable, HttpRequest httpRequest) {
        var text = searchText == null ? "" : searchText.toLowerCase();
        List<Option> options = List.of();
        try {
            options = clients.integrations().stream()
                    .map(i -> new Option(i.crsHotelCode(), label(i)))
                    .filter(o -> o.label().toLowerCase().contains(text))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("The integrations could not be listed: {}", e.getMessage());
        }
        return new ListingData<>(new Page<>(searchText, options.size(), 0, options.size(), options));
    }

    static String label(IntegrationView i) {
        return "%s ↔ %s · %s".formatted(i.crsHotelCode(), i.pmsHotelCode(), i.status());
    }
}
