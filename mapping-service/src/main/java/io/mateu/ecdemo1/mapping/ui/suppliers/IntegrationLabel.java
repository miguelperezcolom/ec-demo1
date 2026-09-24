package io.mateu.ecdemo1.mapping.ui.suppliers;

import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupLabelSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The chosen integration as the options name it. */
@Service
@RequiredArgsConstructor
public class IntegrationLabel implements LookupLabelSupplier {

    final IntegrationClients clients;

    @Override
    public String label(String fieldId, Object value, HttpRequest httpRequest) {
        if (value == null) {
            return "";
        }
        try {
            return clients.integration(value.toString()).map(IntegrationOptions::label).orElse(value.toString());
        } catch (RuntimeException e) {
            return value.toString();
        }
    }
}
