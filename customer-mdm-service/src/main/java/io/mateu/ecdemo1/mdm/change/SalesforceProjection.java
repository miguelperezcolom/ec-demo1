package io.mateu.ecdemo1.mdm.change;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.mdm.resolution.Normalizer;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.outbox.CustomerEvents;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.Xref;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Salesforce is the master of the customer's data; the MDM keeps a projection of it. When Salesforce
 * says a contact changed — by hand, or by an approved change request — the MDM reads it and takes its
 * data as its own; if anything actually changed — or a change a hotel proposed was decided — it says
 * so on the customers topic, and the hotels learn it from there (the front office's kardex, Opera's
 * guest profiles). A write of the MDM's own echoing back changes nothing, and goes no further.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesforceProjection {

    final SalesforceClient salesforce;
    final CustomerRepository customers;
    final CustomerEvents events;
    final Xrefs xrefs;
    final Clock clock;

    /**
     * Reads the customer's contact and projects it.
     *
     * @param requestId the change request this follows the decision of, if any: the hotels learn the
     *                  decision even when the data did not change (a rejection)
     */
    @Transactional
    public boolean refresh(String customerId, String requestId, String decision) {
        var customer = customers.findById(customerId).orElse(null);
        while (customer != null && customer.aliasOf != null) {
            customer = customers.findById(customer.aliasOf).orElse(null);
        }
        if (customer == null) {
            log.debug("Salesforce speaks of {}, which this MDM does not have", customerId);
            return false;
        }
        var contact = salesforce.contactByMdmId(customer.id);
        var changed = contact.isPresent() && apply(customer, contact.get());
        if (contact.isPresent()) {
            xrefs.record(customer.id, Xref.Target.SALESFORCE, contact.get().path("Id").asText(null), null);
            customer.salesforceContactId = contact.get().path("Id").asText(customer.salesforceContactId);
        }
        if (changed) {
            customer.version++;
            customer.updatedAt = clock.instant();
            // It is Salesforce's own data: nothing to send back.
            customer.salesforceState = SalesforceState.PROJECTED;
            customer.projectedAt = clock.instant();
            log.info("{} changed in Salesforce: projected (v{})", customer.id, customer.version);
        }
        customers.save(customer);
        if (changed || requestId != null) {
            // Whoever holds a copy of the customer learns it from the customers topic.
            events.changed(customer, changed, requestId, decision);
        }
        return changed;
    }

    /** Salesforce's values over the projection's; whether any differed. */
    static boolean apply(Customer c, JsonNode k) {
        var before = snapshot(c);
        c.firstName = text(k, "FirstName");
        c.lastName = text(k, "LastName");
        c.email = text(k, "Email");
        c.phone = text(k, "Phone");
        var birth = text(k, "Birthdate");
        c.birthDate = birth == null ? null : LocalDate.parse(birth);
        c.nationality = text(k, "Nationality__c");
        c.documentType = text(k, "Document_Type__c");
        c.documentNumber = text(k, "Document_Number__c");
        c.emailKey = Normalizer.email(c.email);
        c.documentKey = Normalizer.document(c.documentType, c.documentNumber);
        return !before.equals(snapshot(c));
    }

    static java.util.List<Object> snapshot(Customer c) {
        return java.util.Arrays.asList(c.firstName, c.lastName, c.email, c.phone, c.birthDate, c.nationality,
                c.documentType, c.documentNumber);
    }

    static String text(JsonNode k, String field) {
        var value = k.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    static boolean same(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
