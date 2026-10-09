package io.mateu.ecdemo1.mdm.kardex;

import io.mateu.ecdemo1.integration.model.command.CustomerCommand.RecordKardex;
import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.change.Xrefs;
import io.mateu.ecdemo1.mdm.documents.CustomerDocuments;
import io.mateu.ecdemo1.mdm.outbox.CustomerEvents;
import io.mateu.ecdemo1.mdm.scan.ScannedIdentities;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerDocument;
import io.mateu.ecdemo1.mdm.store.CustomerDocumentRepository;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.Xref;
import io.mateu.ecdemo1.mdm.resolution.Normalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A pax's kárdex, filled in at the desk with the guest ({@link RecordKardex}): what they declare of
 * themselves. It is the guest's own word at the counter, so it is <b>the latest that counts</b> — sex,
 * language, address, place of birth, fax, Riu Class number and advertising consent replace what the
 * customer had. What it does not replace: the birth date and the nationality only fill blanks (a scan's
 * document is stronger evidence), and the name, email and phone go to Salesforce as a change request
 * ({@code ProposeChange}), not here. The document gets its issue date and expiry.
 *
 * <p>Like any change of the golden record: to Salesforce (no Case) and to the hotels on the customers topic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Kardexes {

    final CustomerRepository customers;
    final ScannedIdentities scans;
    final CustomerDocuments documents;
    final CustomerDocumentRepository documentRows;
    final Xrefs xrefs;
    final CustomerEvents events;
    final Clock clock;

    /** What the kárdex did: whose it is, and the fields it changed. */
    public record Outcome(String customerId, List<String> changed) {
    }

    /** In the caller's transaction: the command's, with its inbox entry. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome record(RecordKardex k) {
        var customer = scans.paxCustomer(k.customerId(), k.hotelCode(), k.locator(), k.pax(), k.firstName(), k.lastName());
        if (customer == null || customer.status == CustomerStatus.MERGED) {
            // A pax the MDM does not know yet: their scan, or the reservation, will make them a customer;
            // the desk keeps the kárdex and sends it again with the next edit.
            log.info("{}/{} pax {}: kárdex of a pax the MDM does not know — not kept", k.hotelCode(), k.locator(), k.pax());
            return new Outcome(null, List.of());
        }
        var changed = new ArrayList<String>();
        set(changed, "sexo", upper(k.sex()), () -> customer.sex, v -> customer.sex = v);
        set(changed, "idioma", lower(k.language()), () -> customer.language, v -> customer.language = v);
        set(changed, "lugar de nacimiento", trim(k.birthPlace()), () -> customer.birthPlace, v -> customer.birthPlace = v);
        set(changed, "dirección", trim(k.address()), () -> customer.address, v -> customer.address = v);
        set(changed, "población", trim(k.city()), () -> customer.city, v -> customer.city = v);
        set(changed, "código postal", trim(k.postalCode()), () -> customer.postalCode, v -> customer.postalCode = v);
        set(changed, "provincia", trim(k.province()), () -> customer.province, v -> customer.province = v);
        set(changed, "país de residencia", upper(k.countryOfResidence()), () -> customer.countryOfResidence,
                v -> customer.countryOfResidence = v);
        set(changed, "fax", trim(k.fax()), () -> customer.fax, v -> customer.fax = v);
        set(changed, "Riu Class", upper(k.riuClass()), () -> customer.riuClass, v -> customer.riuClass = v);
        if (k.marketingConsent() != null && !k.marketingConsent().equals(customer.marketingConsent)) {
            customer.marketingConsent = k.marketingConsent();
            changed.add("publicidad");
        }
        // what a document is stronger evidence of: only where the customer has nothing
        if (customer.birthDate == null && k.birthDate() != null) {
            customer.birthDate = k.birthDate();
            changed.add("fecha de nacimiento");
        }
        if (blank(customer.nationality) && !blank(k.nationality())) {
            customer.nationality = upper(k.nationality());
            changed.add("nacionalidad");
        }
        if (!blank(k.documentNumber())) {
            var country = blank(customer.nationality) ? upper(k.nationality()) : customer.nationality;
            var row = documents.add(customer.id, k.documentType(), k.documentNumber(), country, k.documentExpiry(),
                    CustomerDocument.Origin.CUSTOMER.name());
            if (row != null && k.documentIssueDate() != null && !k.documentIssueDate().equals(row.issued)) {
                row.issued = k.documentIssueDate();
                documentRows.save(row);
                changed.add("fecha de expedición");
            }
            if (blank(customer.documentNumber)) {
                customer.documentType = k.documentType();
                customer.documentNumber = k.documentNumber().trim();
                customer.documentKey = Normalizer.document(customer.documentType, customer.documentNumber);
                changed.add("documento");
            }
        }
        if (!blank(k.riuClass())) {
            xrefs.record(customer.id, Xref.Target.RIU_CLASS, upper(k.riuClass()), "kardex " + k.hotelCode());
        }
        customer.kardexAt = clock.instant();
        customer.kardexHotel = k.hotelCode();
        if (!changed.isEmpty()) {
            customer.version++;
            customer.updatedAt = clock.instant();
            if (customer.salesforceState != SalesforceState.REMOVED && customer.salesforceState != SalesforceState.ANONYMIZED) {
                customer.salesforceState = SalesforceState.PENDING;
            }
        }
        customers.save(customer);
        if (!changed.isEmpty()) {
            events.changed(customer, true, null, null, null);
        }
        log.info("{}: kárdex from {}/{} pax {} — {}", customer.id, k.hotelCode(), k.locator(), k.pax(),
                changed.isEmpty() ? "nothing new" : String.join(", ", changed));
        return new Outcome(customer.id, changed);
    }

    /** A merge: the kárdex fields the survivor lacks, from the absorbed customer. */
    public static void fillFrom(Customer survivor, Customer absorbed, List<String> notes) {
        fill(notes, "sex", () -> survivor.sex, absorbed.sex, v -> survivor.sex = v);
        fill(notes, "language", () -> survivor.language, absorbed.language, v -> survivor.language = v);
        fill(notes, "birthPlace", () -> survivor.birthPlace, absorbed.birthPlace, v -> survivor.birthPlace = v);
        fill(notes, "address", () -> survivor.address, absorbed.address, v -> survivor.address = v);
        fill(notes, "city", () -> survivor.city, absorbed.city, v -> survivor.city = v);
        fill(notes, "postalCode", () -> survivor.postalCode, absorbed.postalCode, v -> survivor.postalCode = v);
        fill(notes, "province", () -> survivor.province, absorbed.province, v -> survivor.province = v);
        fill(notes, "countryOfResidence", () -> survivor.countryOfResidence, absorbed.countryOfResidence,
                v -> survivor.countryOfResidence = v);
        fill(notes, "fax", () -> survivor.fax, absorbed.fax, v -> survivor.fax = v);
        fill(notes, "riuClass", () -> survivor.riuClass, absorbed.riuClass, v -> survivor.riuClass = v);
        if (survivor.marketingConsent == null && absorbed.marketingConsent != null) {
            survivor.marketingConsent = absorbed.marketingConsent;
            notes.add("marketingConsent: absorbed");
        }
    }

    static void fill(List<String> notes, String field, Supplier<String> survivor, String absorbed, Consumer<String> set) {
        if (survivor.get() == null && absorbed != null) {
            set.accept(absorbed);
            notes.add(field + ": absorbed");
        }
    }

    static void set(List<String> changed, String label, String value, Supplier<String> current, Consumer<String> set) {
        if (value != null && !Objects.equals(value, current.get())) {
            set.accept(value);
            changed.add(label);
        }
    }

    static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String upper(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    static String lower(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
