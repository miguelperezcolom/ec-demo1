package io.mateu.ecdemo1.mdm.resolution;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.integration.model.customer.IdentityRequest;
import io.mateu.ecdemo1.integration.model.customer.ResolvedIdentity;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.Source;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * «Resolver Identidad» (HLA CRM-MDM, F001/F002): the one step on the reservation's path, so it
 * decides the match and nothing else. Cleaning happens later, in Salesforce.
 *
 * <p>It matches only on what is certain — a document, or an email together with the name — and
 * when in doubt it does not merge: it creates a provisional customer. Merging two different people
 * touches folios, invoices and privacy; leaving a duplicate is what cleaning is there to catch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityResolution {

    static final EnumSet<CustomerStatus> LIVE = EnumSet.of(CustomerStatus.PROVISIONAL, CustomerStatus.CONSOLIDATED);

    final CustomerRepository customers;
    final SourceRepository sources;
    final Clock clock;

    @Transactional
    public List<ResolvedIdentity> resolve(IdentityRequest request) {
        var resolved = new ArrayList<ResolvedIdentity>();
        var inReservation = new ArrayList<Customer>();
        var passengers = request.passengers() == null ? List.<Person>of() : request.passengers();
        for (int i = 0; i < passengers.size(); i++) {
            var person = passengers.get(i);
            var key = Source.key(request.hotelCode(), request.locator(), i);
            var source = sources.findById(key).orElse(null);
            Customer customer;
            String matchedBy;
            if (source != null) {
                customer = survivorOf(source.customerId);
                matchedBy = "SOURCE";
            } else {
                var byDocument = unique(byDocument(Normalizer.document(person.documentType(), person.documentNumber())));
                var name = Normalizer.name(person.firstName(), person.lastName());
                var byEmail = unique(byEmail(Normalizer.email(person.email())).stream()
                        .filter(c -> name != null && name.equals(Normalizer.name(c.firstName, c.lastName)))
                        .toList());
                var sameInReservation = inReservation.stream().filter(c -> sameWithin(c, person)).findFirst().orElse(null);
                if (byDocument != null) {
                    customer = byDocument;
                    matchedBy = "DOCUMENT";
                } else if (byEmail != null) {
                    customer = byEmail;
                    matchedBy = "EMAIL";
                } else if (sameInReservation != null) {
                    // The holder is usually also a guest of one of the rooms, with less data.
                    customer = sameInReservation;
                    matchedBy = "RESERVATION";
                } else {
                    customer = provisional(person);
                    matchedBy = "NEW";
                }
                source = new Source();
                source.sourceKey = key;
                source.customerId = customer.id;
                source.firstCustomerId = customer.id;
                source.hotelCode = request.hotelCode();
                source.locator = request.locator();
                source.passenger = i;
                source.firstSeen = clock.instant();
            }
            source.lastSeen = clock.instant();
            sources.save(source);
            fillBlanks(customer, person);
            inReservation.add(customer);
            resolved.add(new ResolvedIdentity(i, customer.id, customer.status, matchedBy));
        }
        log.info("{}/{}: {}", request.hotelCode(), request.locator(),
                resolved.stream().map(r -> r.customerId() + " (" + r.matchedBy() + ")").toList());
        return resolved;
    }

    /** A customer by any code it ever had: an absorbed one answers with its survivor. */
    @Transactional(readOnly = true)
    public Customer survivorOf(String customerId) {
        var customer = customers.findById(customerId).orElseThrow(() -> new NoSuchElementException("No customer " + customerId));
        var hops = 0;
        while (customer.aliasOf != null && hops++ < 20) {
            customer = customers.findById(customer.aliasOf).orElseThrow();
        }
        return customer;
    }

    Customer provisional(Person person) {
        var c = new Customer();
        c.id = "C-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        c.status = CustomerStatus.PROVISIONAL;
        c.createdAt = clock.instant();
        c.salesforceState = SalesforceState.PENDING;
        return c;
    }

    /**
     * What a reservation brings fills what the customer did not have. It does not overwrite: which
     * value wins between two sources is survivorship, decided when cleaning merges, not here.
     */
    void fillBlanks(Customer c, Person p) {
        var changed = false;
        if (c.firstName == null && p.firstName() != null) { c.firstName = p.firstName(); changed = true; }
        if (c.lastName == null && p.lastName() != null) { c.lastName = p.lastName(); changed = true; }
        if (c.email == null && p.email() != null) { c.email = p.email(); changed = true; }
        if (c.phone == null && p.phone() != null) { c.phone = p.phone(); changed = true; }
        if (c.nationality == null && p.nationality() != null) { c.nationality = p.nationality(); changed = true; }
        if (c.birthDate == null && p.birthDate() != null) { c.birthDate = p.birthDate(); changed = true; }
        if (c.documentNumber == null && p.documentNumber() != null) {
            c.documentType = p.documentType();
            c.documentNumber = p.documentNumber();
            changed = true;
        }
        if (changed || c.updatedAt == null) {
            c.emailKey = Normalizer.email(c.email);
            c.documentKey = Normalizer.document(c.documentType, c.documentNumber);
            c.version++;
            c.updatedAt = clock.instant();
            if (c.salesforceState != SalesforceState.REMOVED && c.status != CustomerStatus.MERGED) {
                c.salesforceState = SalesforceState.PENDING;
            }
            customers.save(c);
        }
    }

    static boolean sameWithin(Customer c, Person p) {
        var name = Normalizer.name(p.firstName(), p.lastName());
        if (name == null || !name.equals(Normalizer.name(c.firstName, c.lastName))) {
            return false;
        }
        var email = Normalizer.email(p.email());
        var document = Normalizer.document(p.documentType(), p.documentNumber());
        return (email == null || c.emailKey == null || email.equals(c.emailKey))
                && (document == null || c.documentKey == null || document.equals(c.documentKey));
    }

    // A null key would be a query for every customer without one.
    List<Customer> byDocument(String documentKey) {
        return documentKey == null ? List.of() : customers.findByDocumentKeyAndStatusIn(documentKey, LIVE);
    }

    List<Customer> byEmail(String emailKey) {
        return emailKey == null ? List.of() : customers.findByEmailKeyAndStatusIn(emailKey, LIVE);
    }

    /** One candidate is a match; none, or more than one, is not — ambiguity is for a person to settle. */
    static Customer unique(List<Customer> candidates) {
        return candidates.size() == 1 ? candidates.get(0) : null;
    }
}
