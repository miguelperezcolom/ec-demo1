package io.mateu.ecdemo1.mdm.documents;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.outbox.CustomerEvents;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.resolution.Normalizer;
import io.mateu.ecdemo1.mdm.store.CustomerDocument;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

/**
 * A document added to a customer directly — the demo seeds its known customers so. As a scan that nobody
 * else's document is: one more of the customer's, and their main one if they had none.
 */
@Service
@RequiredArgsConstructor
public class DocumentDesk {

    final IdentityResolution resolution;
    final CustomerRepository customers;
    final CustomerDocuments documents;
    final CustomerEvents events;
    final Clock clock;

    /** @param origin SCAN, RESERVATION or CUSTOMER; CUSTOMER if none is said */
    public record NewDocument(String type, String number, String issuingCountry, LocalDate expiry, String origin) {
    }

    @Transactional
    public void add(String customerId, NewDocument d) {
        if (d == null || Normalizer.documentNumber(d.number()) == null) {
            throw new IllegalArgumentException("A document needs a number");
        }
        var origin = d.origin() == null || d.origin().isBlank() ? CustomerDocument.Origin.CUSTOMER.name()
                : d.origin().trim().toUpperCase(Locale.ROOT);
        if (Arrays.stream(CustomerDocument.Origin.values()).noneMatch(o -> o.name().equals(origin))) {
            throw new IllegalArgumentException("origin is one of " + Arrays.toString(CustomerDocument.Origin.values()));
        }
        var c = resolution.survivorOf(customerId);
        var country = d.issuingCountry() == null || d.issuingCountry().isBlank() ? c.nationality : d.issuingCountry();
        var added = !documents.holds(c.id, d.number(), country);
        documents.add(c.id, d.type(), d.number(), country, d.expiry(), origin);
        var main = c.documentNumber == null || c.documentNumber.isBlank();
        if (main) {
            c.documentType = d.type();
            c.documentNumber = d.number();
            c.documentKey = Normalizer.document(c.documentType, c.documentNumber);
            if (c.salesforceState != SalesforceState.REMOVED && c.salesforceState != SalesforceState.ANONYMIZED
                    && c.status != CustomerStatus.MERGED) {
                // The main document is the contact's: to Salesforce, as a scan's would.
                c.salesforceState = SalesforceState.PENDING;
            }
        }
        if (main || added) {
            c.version++;
            c.updatedAt = clock.instant();
            customers.save(c);
            events.changed(c, true, null, null, null);
        }
    }
}
