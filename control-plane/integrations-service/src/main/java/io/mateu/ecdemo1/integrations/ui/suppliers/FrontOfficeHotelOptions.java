package io.mateu.ecdemo1.integrations.ui.suppliers;

import io.mateu.ecdemo1.integrations.clients.Services;
import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
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
 * The hotels the front office serves, asked of it at the address typed in the form (the configured one
 * if none is). If it does not answer, or does not say, the list comes back empty: the registration then
 * finds out, and says why.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FrontOfficeHotelOptions implements LookupOptionsSupplier {

    final Services services;
    final IntegrationsProperties properties;

    @Override
    public ListingData<Option> search(String fieldId, String searchText, Pageable pageable, HttpRequest httpRequest) {
        var text = searchText == null ? "" : searchText.toLowerCase();
        var url = frontOfficeUrl(httpRequest);
        List<Option> options = List.of();
        try {
            var hotels = services.frontOfficeHotels(url);
            options = hotels == null ? List.of() : hotels.stream()
                    .map(h -> new Option(h.code(), "%s · %s (Opera %s)".formatted(h.code(), h.name(), h.pmsHotelCode())))
                    .filter(o -> o.label().toLowerCase().contains(text))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Front office {} did not list its hotels: {}", url, e.getMessage());
        }
        return new ListingData<>(new Page<>(searchText, options.size(), 0, options.size(), options));
    }

    String frontOfficeUrl(HttpRequest httpRequest) {
        try {
            var typed = httpRequest == null ? null : httpRequest.getString("frontOfficeUrl");
            if (typed != null && !typed.isBlank()) {
                return typed.trim();
            }
        } catch (RuntimeException e) {
            // a request without the form's state: the configured front office
        }
        return properties.frontOfficeUrl();
    }
}
