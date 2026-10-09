package io.mateu.ecdemo1.mdm.documents;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.resolution.Normalizer;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerDocument;
import io.mateu.ecdemo1.mdm.store.CustomerDocumentRepository;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A customer's identity documents ({@link CustomerDocument}): adding one, who holds one, and a merge's
 * documents going to the survivor. In the caller's transaction, so a document is recorded with the
 * change that brought it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerDocuments {

    static final EnumSet<CustomerStatus> LIVE = EnumSet.of(CustomerStatus.PROVISIONAL, CustomerStatus.CONSOLIDATED);

    final CustomerDocumentRepository documents;
    final CustomerRepository customers;
    final Clock clock;

    /**
     * Records a document of the customer's. Seen again, it is the same row — its last sighting and its
     * expiry, if one is said, are updated. "Again" is the same number from the same country; a row with
     * no country and the same number is that document too, and learns the country now said.
     *
     * @param origin one of {@link CustomerDocument.Origin}
     * @return the document, or null when there is no number
     */
    @Transactional
    public CustomerDocument add(String customerId, String type, String number, String issuingCountry,
                                LocalDate expiry, String origin) {
        var numberKey = Normalizer.documentNumber(number);
        if (customerId == null || numberKey == null) {
            return null;
        }
        var country = CustomerDocument.country(issuingCountry);
        var now = clock.instant();
        var same = documents.findByCustomerIdAndNumberKey(customerId, numberKey).stream()
                .filter(d -> country == null || d.issuingCountry == null || country.equals(d.issuingCountry))
                .min(Comparator.comparing((CustomerDocument d) -> !Objects.equals(country, d.issuingCountry)))
                .orElse(null);
        if (same != null) {
            if (same.issuingCountry == null && country != null) {
                same.issuingCountry = country;
            }
            if (expiry != null) {
                same.expiry = expiry;
            }
            var read = CustomerDocument.type(type);
            if (CustomerDocument.Type.OTHER.name().equals(same.type) && !CustomerDocument.Type.OTHER.name().equals(read)) {
                // "DOC" first, "PASSPORT" later: what it really is.
                same.type = read;
            }
            same.lastSeenAt = now;
            return documents.save(same);
        }
        var d = new CustomerDocument();
        d.id = UUID.randomUUID().toString();
        d.customerId = customerId;
        d.type = CustomerDocument.type(type);
        d.numberKey = numberKey;
        d.number = number.trim();
        d.issuingCountry = country;
        d.expiry = expiry;
        d.origin = origin;
        d.firstSeenAt = now;
        d.lastSeenAt = now;
        log.info("{}: document {} {} ({}) added, from {}", customerId, d.type, d.number, country, origin);
        return documents.save(d);
    }

    /**
     * The live customers holding the document — survivors, each once. With a country, the document
     * that country issued; without, any document with that number.
     */
    @Transactional(readOnly = true)
    public List<Customer> owners(String number, String issuingCountry) {
        var numberKey = Normalizer.documentNumber(number);
        if (numberKey == null) {
            return List.of();
        }
        var country = CustomerDocument.country(issuingCountry);
        var rows = country == null ? documents.findByNumberKey(numberKey)
                : documents.findByIssuingCountryAndNumberKey(country, numberKey);
        var owners = new LinkedHashMap<String, Customer>();
        for (var row : rows) {
            var owner = survivorOf(row.customerId);
            if (owner != null && LIVE.contains(owner.status)) {
                owners.putIfAbsent(owner.id, owner);
            }
        }
        return List.copyOf(owners.values());
    }

    /** Whether the customer has the document already — as {@link #add} would find it again. */
    @Transactional(readOnly = true)
    public boolean holds(String customerId, String number, String issuingCountry) {
        var numberKey = Normalizer.documentNumber(number);
        var country = CustomerDocument.country(issuingCountry);
        return customerId != null && numberKey != null && documents.findByCustomerIdAndNumberKey(customerId, numberKey).stream()
                .anyMatch(d -> country == null || d.issuingCountry == null || country.equals(d.issuingCountry));
    }

    @Transactional(readOnly = true)
    public List<CustomerDocument> of(String customerId) {
        return documents.findByCustomerIdOrderByFirstSeenAtAsc(customerId);
    }

    /** A merge: the absorbed customer's documents are the survivor's now — one row per document still. */
    @Transactional
    public void moveTo(String absorbedId, String survivorId) {
        for (var d : documents.findByCustomerIdOrderByFirstSeenAtAsc(absorbedId)) {
            var kept = add(survivorId, d.type, d.number, d.issuingCountry, d.expiry, d.origin);
            if (kept != null && kept != d && d.firstSeenAt != null
                    && (kept.firstSeenAt == null || d.firstSeenAt.isBefore(kept.firstSeenAt))) {
                kept.firstSeenAt = d.firstSeenAt;
                kept.origin = d.origin;
                documents.save(kept);
            }
            documents.delete(d);
        }
    }

    /** The customer by any code it had; null if there is none (a row left by a reset, say). */
    Customer survivorOf(String customerId) {
        var customer = customerId == null ? null : customers.findById(customerId).orElse(null);
        var hops = 0;
        while (customer != null && customer.aliasOf != null && hops++ < 20) {
            customer = customers.findById(customer.aliasOf).orElse(null);
        }
        return customer;
    }
}
