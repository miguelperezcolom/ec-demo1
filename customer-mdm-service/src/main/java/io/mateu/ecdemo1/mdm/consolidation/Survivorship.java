package io.mateu.ecdemo1.mdm.consolidation;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.store.Consolidation;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import io.mateu.ecdemo1.mdm.resolution.Normalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * «Survivorship» (HLA CRM-MDM, F004): two customers are one, and the MDM — not Salesforce — decides
 * what the survivor is. The rule, field by field: the value the steward kept on the surviving
 * contact, which is data a person looked at; else what the MDM already had; else the absorbed
 * customer's. Nothing is deleted: the absorbed customer becomes an alias of the survivor, and its
 * reservations are re-pointed, and the merge is announced on the customers topic, whose subscribers
 * carry the survivor's code to the PMS (F005).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Survivorship {

    final CustomerRepository customers;
    final SourceRepository sources;
    final ConsolidationRepository consolidations;
    final io.mateu.ecdemo1.mdm.outbox.CustomerEvents events;
    final Clock clock;

    @Transactional
    public void merged(String survivorId, String absorbedId, String survivorContactId, String absorbedContactId,
                       JsonNode steward, String via) {
        var survivor = customers.findById(survivorId).orElseThrow(() -> new NoSuchElementException("No customer " + survivorId));
        var absorbed = customers.findById(absorbedId).orElseThrow(() -> new NoSuchElementException("No customer " + absorbedId));
        var notes = new ArrayList<String>();
        pick(notes, "firstName", Consolidations.text(steward, "FirstName"), () -> survivor.firstName, () -> absorbed.firstName, v -> survivor.firstName = v);
        pick(notes, "lastName", Consolidations.text(steward, "LastName"), () -> survivor.lastName, () -> absorbed.lastName, v -> survivor.lastName = v);
        pick(notes, "email", Consolidations.text(steward, "Email"), () -> survivor.email, () -> absorbed.email, v -> survivor.email = v);
        pick(notes, "phone", Consolidations.text(steward, "Phone"), () -> survivor.phone, () -> absorbed.phone, v -> survivor.phone = v);
        pick(notes, "nationality", Consolidations.text(steward, "Nationality__c"), () -> survivor.nationality, () -> absorbed.nationality, v -> survivor.nationality = v);
        pick(notes, "birthDate", Consolidations.text(steward, "Birthdate"),
                () -> survivor.birthDate == null ? null : survivor.birthDate.toString(),
                () -> absorbed.birthDate == null ? null : absorbed.birthDate.toString(),
                v -> survivor.birthDate = v == null ? null : LocalDate.parse(v));
        // The document is one thing — type and number travel together.
        var stewardNumber = Consolidations.text(steward, "Document_Number__c");
        if (stewardNumber != null) {
            survivor.documentType = Consolidations.text(steward, "Document_Type__c");
            survivor.documentNumber = stewardNumber;
            notes.add("document: steward");
        } else if (survivor.documentNumber == null && absorbed.documentNumber != null) {
            survivor.documentType = absorbed.documentType;
            survivor.documentNumber = absorbed.documentNumber;
            notes.add("document: " + absorbedId);
        }
        survivor.emailKey = Normalizer.email(survivor.email);
        survivor.documentKey = Normalizer.document(survivor.documentType, survivor.documentNumber);
        survivor.status = CustomerStatus.CONSOLIDATED;
        survivor.survivorship = "Merged " + absorbedId + " — " + String.join(", ", notes);
        survivor.salesforceContactId = survivorContactId;
        // What the survivor took from the MDM rather than from the contact goes back to Salesforce.
        survivor.salesforceState = SalesforceState.PENDING;
        touch(survivor);

        absorbed.status = CustomerStatus.MERGED;
        absorbed.aliasOf = survivorId;
        absorbed.salesforceState = SalesforceState.NOT_PROJECTED;
        touch(absorbed);
        // An alias of an alias would take two hops, and the chain would only grow.
        for (var alias : customers.findByAliasOf(absorbedId)) {
            alias.aliasOf = survivorId;
            touch(alias);
        }

        var reservations = new LinkedHashSet<String>();
        for (var source : sources.findByCustomerIdOrderByFirstSeenAsc(absorbedId)) {
            source.customerId = survivorId;
            sources.save(source);
            reservations.add(source.hotelCode + "/" + source.locator);
        }
        var c = consolidation(absorbedId, absorbedContactId, via);
        c.survivorId = survivorId;
        c.survivorContactId = survivorContactId;
        c.appliedAt = clock.instant();
        c.reservations = reservations.size();
        c.reservationKeys = String.join(",", reservations);
        // Carried to the reservations by whoever subscribes to the customers topic (F005): the event
        // leaves with this transaction, so the merge is propagated once it is saved.
        c.propagatedAt = clock.instant();
        c.detail = survivor.survivorship;
        consolidations.save(c);
        events.merged(survivor, absorbedId);
        log.info("{} merged into {} (via {}): {}; {} reservation(s) to carry the new code",
                absorbedId, survivorId, via, notes, reservations.size());
    }

    /** The contact that absorbed it was nobody in the MDM: the absorbed customer lives on as that contact. */
    @Transactional
    public void adopted(String customerId, String oldContactId, String newContactId, JsonNode contact, String via) {
        var customer = customers.findById(customerId).orElseThrow(() -> new NoSuchElementException("No customer " + customerId));
        customer.salesforceContactId = newContactId;
        customer.salesforceState = SalesforceState.PENDING;
        touch(customer);
        var c = consolidation(customerId, oldContactId, via);
        c.survivorId = customerId;
        c.survivorContactId = newContactId;
        c.appliedAt = clock.instant();
        c.propagatedAt = clock.instant();
        c.detail = "Merged into contact " + newContactId + ", which had no MDM id and took this customer's";
        consolidations.save(c);
        log.info("{} now stands for contact {} (via {})", customerId, newContactId, via);
    }

    /** Deleted in Salesforce, not merged: the customer stays, and is not sent there again on its own. */
    @Transactional
    public void removed(String customerId, String contactId, String via) {
        customers.findById(customerId).ifPresent(customer -> {
            customer.salesforceState = SalesforceState.REMOVED;
            customer.salesforceContactId = null;
            touch(customer);
        });
        var c = consolidation(customerId, contactId, via);
        c.appliedAt = clock.instant();
        c.propagatedAt = clock.instant();
        c.detail = "Deleted in Salesforce without a merge: the customer stays in the MDM";
        consolidations.save(c);
        log.warn("{}'s contact {} was deleted in Salesforce, not merged", customerId, contactId);
    }

    Consolidation consolidation(String absorbedId, String absorbedContactId, String via) {
        var c = consolidations.findById(absorbedId).orElseGet(Consolidation::new);
        if (c.absorbedId == null) {
            c.absorbedId = absorbedId;
            c.receivedAt = clock.instant();
            c.via = via;
        }
        c.absorbedContactId = absorbedContactId;
        return c;
    }

    void touch(Customer c) {
        c.version++;
        c.updatedAt = clock.instant();
        customers.save(c);
    }

    static void pick(ArrayList<String> notes, String field, String steward, Supplier<String> survivor, Supplier<String> absorbed,
                     Consumer<String> set) {
        if (steward != null) {
            var before = survivor.get();
            set.accept(steward);
            if (!steward.equals(before)) {
                notes.add(field + ": steward");
            }
        } else if (survivor.get() == null && absorbed.get() != null) {
            set.accept(absorbed.get());
            notes.add(field + ": absorbed");
        }
    }
}
