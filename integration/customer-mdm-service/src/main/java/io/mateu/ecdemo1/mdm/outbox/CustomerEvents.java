package io.mateu.ecdemo1.mdm.outbox;

import io.mateu.ecdemo1.integration.model.customer.CustomerChanged;
import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import io.mateu.ecdemo1.integration.model.customer.GoldenRecord;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerDocumentRepository;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** The MDM's events about a customer, with its golden record and its reservations, into the outbox. */
@Component
@RequiredArgsConstructor
public class CustomerEvents {

    final Outbox outbox;
    final SourceRepository sources;
    final Clock clock;
    final CustomerDocumentRepository documents;

    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(Customer c, boolean dataChanged, String changeRequestId, String decision, String reason) {
        outbox.append(new CustomerChanged(UUID.randomUUID().toString(), clock.instant(), c.id, c.version, golden(c),
                dataChanged, changeRequestId, decision, reason, reservations(c.id)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void merged(Customer survivor, String absorbedId) {
        outbox.append(new CustomersMerged(UUID.randomUUID().toString(), clock.instant(), survivor.id, survivor.version,
                golden(survivor), absorbedId, reservations(survivor.id)));
    }

    /** With every document the MDM knows of the customer, the main one among them. */
    GoldenRecord golden(Customer c) {
        var known = documents.findByCustomerIdOrderByFirstSeenAtAsc(c.id).stream()
                .map(d -> new GoldenRecord.IdentityDocument(d.type, d.number, d.issuingCountry))
                .toList();
        return new GoldenRecord(c.firstName, c.lastName, c.email, c.phone, c.nationality, c.birthDate, c.documentType,
                c.documentNumber, known, profile(c));
    }

    /** The kárdex, if the customer has any of it; null otherwise. */
    static GoldenRecord.Profile profile(Customer c) {
        var p = new GoldenRecord.Profile(c.sex, c.language, c.birthPlace, c.address, c.city, c.postalCode, c.province,
                c.countryOfResidence, c.fax, c.riuClass, c.marketingConsent);
        return java.util.stream.Stream.of(c.sex, c.language, c.birthPlace, c.address, c.city, c.postalCode, c.province,
                c.countryOfResidence, c.fax, c.riuClass).allMatch(java.util.Objects::isNull) && c.marketingConsent == null
                ? null : p;
    }

    List<String> reservations(String customerId) {
        return sources.findByCustomerIdOrderByFirstSeenAsc(customerId).stream()
                .map(s -> s.hotelCode + "/" + s.locator).distinct().toList();
    }
}
