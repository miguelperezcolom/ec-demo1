package io.mateu.ecdemo1.integrations.ui.suppliers;

import io.mateu.ecdemo1.integrations.clients.Services;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupLabelSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrsHotelLabel implements LookupLabelSupplier {

    final Services services;

    @Override
    public String label(String fieldId, Object value, HttpRequest httpRequest) {
        if (value == null) {
            return "";
        }
        return services.crsHotels().stream()
                .filter(h -> h.code().equals(value.toString()))
                .map(h -> h.code() + " · " + h.description())
                .findFirst().orElse(value.toString());
    }
}
