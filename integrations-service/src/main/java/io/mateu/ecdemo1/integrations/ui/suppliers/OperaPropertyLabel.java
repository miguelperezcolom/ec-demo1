package io.mateu.ecdemo1.integrations.ui.suppliers;

import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupLabelSupplier;
import org.springframework.stereotype.Service;

/** The property as it was chosen: its Opera code, which is what the connector puts in x-hotelid. */
@Service
public class OperaPropertyLabel implements LookupLabelSupplier {

    @Override
    public String label(String fieldId, Object value, HttpRequest httpRequest) {
        return value == null ? "" : value.toString();
    }
}
