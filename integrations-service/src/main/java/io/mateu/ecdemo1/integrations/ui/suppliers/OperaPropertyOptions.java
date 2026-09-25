package io.mateu.ecdemo1.integrations.ui.suppliers;

import io.mateu.ecdemo1.integrations.lifecycle.Integrations;
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
 * The properties of the chain in Opera, asked of the tenant with the chain's connection. If the
 * tenant does not answer — no connection configured, or it refuses — the list comes back empty and
 * the property code is typed, which is what a deployment without the enterprise API would do.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OperaPropertyOptions implements LookupOptionsSupplier {

    final Integrations integrations;

    @Override
    public ListingData<Option> search(String fieldId, String searchText, Pageable pageable, HttpRequest httpRequest) {
        var text = searchText == null ? "" : searchText.toLowerCase();
        List<Option> options = List.of();
        try {
            options = integrations.operaProperties().stream()
                    .filter(p -> (p.code() + " " + p.name()).toLowerCase().contains(text))
                    .map(p -> new Option(p.code(), p.code() + " · " + p.name()))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Opera did not list its properties: {}", e.getMessage());
        }
        return new ListingData<>(new Page<>(searchText, options.size(), 0, options.size(), options));
    }
}
