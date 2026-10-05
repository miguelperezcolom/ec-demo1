package io.mateu.ecdemo1.integrations.ui.suppliers;

import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupLabelSupplier;
import org.springframework.stereotype.Service;

/** The front office's hotel as it was chosen: its code, which is what the integration keeps. */
@Service
public class FrontOfficeHotelLabel implements LookupLabelSupplier {

    @Override
    public String label(String fieldId, Object value, HttpRequest httpRequest) {
        return value == null ? "" : value.toString();
    }
}
