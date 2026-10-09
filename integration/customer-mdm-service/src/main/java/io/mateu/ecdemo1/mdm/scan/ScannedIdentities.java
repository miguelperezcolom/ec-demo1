package io.mateu.ecdemo1.mdm.scan;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.mateu.ecdemo1.integration.model.command.CustomerCommand.RecordScannedIdentity;
import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.change.ChangeRequests;
import io.mateu.ecdemo1.mdm.consolidation.Survivorship;
import io.mateu.ecdemo1.mdm.documents.CustomerDocuments;
import io.mateu.ecdemo1.mdm.outbox.CustomerEvents;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.resolution.Normalizer;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerDocument;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.Source;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A pax's identity document, read by the desk's scanner. What a document says is <b>trusted</b>: it is
 * the person's own paper, not what somebody typed into a booking. So:
 *
 * <ul>
 *   <li>What the customer lacks — the document, the birth date, the nationality, a name — is filled at
 *       once and goes to Salesforce as any change of the golden record does: no Case, nobody to ask.</li>
 *   <li>A document nobody else holds is one more of the customer's ({@link CustomerDocuments}): added beside
 *       the main one they had, which stays as it is. Another document is not a contradiction — the same
 *       person shows a DNI one year and a passport the next.</li>
 *   <li>What it <em>contradicts</em> — another name, another birth date — is not overwritten: Salesforce
 *       is the master, so it is proposed there as a change request (a Case), as a change the desk makes by
 *       hand is.</li>
 *   <li>A document is the strongest key there is. If the scanned one already belongs to another
 *       customer, the pax <em>is</em> that customer: the provisional record the reservation made is
 *       consolidated into the one holding the document, by the same survivorship a merge in Salesforce
 *       applies — alias, reservations re-pointed, the merge announced — and the two contacts are merged
 *       in Salesforce too ({@link SalesforceMerges}). Only when it is certain: the document is the
 *       holder's alone, the pax had no other main document, and the name the document shows is the
 *       holder's. Anything less is doubt, and the MDM never merges on doubt: the document goes as a change
 *       request instead, for a person to decide.</li>
 *   <li>When the desk <em>confirmed</em> who the pax is — by their Riu Class number or their email,
 *       {@code confirmedCustomerId} — that is not doubt: the pax's code is consolidated into that customer
 *       the same way (via DESK_CONFIRMED), and the document joins theirs.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScannedIdentities {

    static final EnumSet<CustomerStatus> LIVE = EnumSet.of(CustomerStatus.PROVISIONAL, CustomerStatus.CONSOLIDATED);
    /** The passenger number of a pax the desk scanned and the reservation did not list: 100 + its pax number. */
    static final int SCANNED_PAX = 100;

    final CustomerRepository customers;
    final SourceRepository sources;
    final IdentityResolution resolution;
    final Survivorship survivorship;
    final ConsolidationRepository consolidations;
    final ChangeRequests changeRequests;
    final CustomerEvents events;
    final CustomerDocuments documents;
    final Clock clock;

    /**
     * What the scan did.
     *
     * @param matchedBy       how the pax was found: CUSTOMER (the hotel knew it), RESERVATION (its passenger),
     *                        DOCUMENT (the document's holder), CONFIRMED (the customer the desk confirmed), NEW
     * @param filled          the fields the customer did not have and now has
     * @param absorbedId      the customer consolidated into this one because the document is this one's, or
     *                        because the desk confirmed the pax is this one
     * @param changeRequestId the request opened for what the document contradicts
     */
    public record Outcome(String customerId, String matchedBy, List<String> filled, String absorbedId,
                          String changeRequestId, List<String> contradictions) {
    }

    /** In the caller's transaction: the command's, with its inbox entry. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome record(RecordScannedIdentity scan) {
        var numberKey = Normalizer.documentNumber(scan.documentNumber());
        if (numberKey == null) {
            throw new IllegalArgumentException("A scan without a document number: " + scan.commandId());
        }
        // Who issued it, if the scanner said; else the nationality, as for any document.
        var country = blank(scan.issuingCountry()) ? scan.nationality() : scan.issuingCountry();
        var pax = paxCustomer(scan);
        var confirmed = confirmedCustomer(scan);
        Customer target;
        String matchedBy;
        String absorbedId = null;
        String documentHeldBy = null;
        if (confirmed != null) {
            // The desk asked the guest — their Riu Class card, their email — and they are that customer: not
            // a guess of the MDM's, so the pax's code is consolidated into theirs whatever it held.
            target = confirmed;
            matchedBy = "CONFIRMED";
            if (pax == null) {
                link(scan, confirmed);
            } else if (!pax.id.equals(confirmed.id)) {
                consolidate(confirmed, pax, "DESK_CONFIRMED", "Confirmado en recepción en " + scan.hotelCode() + "/"
                        + scan.locator() + " (pax " + scan.pax() + ") como " + confirmed.id + "; documento "
                        + scan.documentNumber() + " escaneado. ");
                absorbedId = pax.id;
            }
            var others = documents.owners(scan.documentNumber(), country).stream()
                    .filter(c -> !c.id.equals(confirmed.id))
                    .toList();
            if (!others.isEmpty()) {
                // Confirming who the guest is says nothing of a third customer holding their document.
                documentHeldBy = String.join(", ", others.stream().map(c -> c.id).toList());
                log.warn("{}: the scanned document is also {}'s: not added, proposed", confirmed.id, documentHeldBy);
            }
        } else {
            var holders = documents.owners(scan.documentNumber(), country).stream()
                    .filter(c -> pax == null || !c.id.equals(pax.id))
                    .toList();
            if (pax == null) {
                if (holders.size() == 1) {
                    target = holders.get(0);
                    matchedBy = "DOCUMENT";
                } else {
                    // Nobody, or more than one — duplicates already, for cleaning to settle: a customer of its own.
                    target = customers.save(provisional());
                    matchedBy = "NEW";
                }
                link(scan, target);
            } else if (holders.size() == 1 && sameDocumentOrNone(pax, numberKey) && sameName(holders.get(0), scan)) {
                var holder = holders.get(0);
                consolidate(holder, pax, "SCAN", "Documento " + scan.documentNumber() + " escaneado en " + scan.hotelCode()
                        + "/" + scan.locator() + " (pax " + scan.pax() + "): ya era de " + holder.id + ". ");
                target = holder;
                matchedBy = "DOCUMENT";
                absorbedId = pax.id;
            } else {
                target = pax;
                matchedBy = scan.customerId() != null && scan.customerId().equals(pax.id) ? "CUSTOMER" : "RESERVATION";
                if (!holders.isEmpty()) {
                    // The document is someone else's, and something does not add up: not merged, proposed.
                    documentHeldBy = String.join(", ", holders.stream().map(c -> c.id).toList());
                    log.warn("{}: the scanned document is also {}'s, but not certainly the same person: not merged",
                            pax.id, documentHeldBy);
                }
            }
        }

        // A document nobody else holds is one more of the customer's — beside the one they had, if any: the
        // same person shows a DNI one year and a passport the next, and neither contradicts the other.
        var documentAdded = false;
        if (documentHeldBy == null) {
            documentAdded = !documents.holds(target.id, scan.documentNumber(), country);
            documents.add(target.id, scan.documentType(), scan.documentNumber(), country, scan.documentExpiry(),
                    CustomerDocument.Origin.SCAN.name());
        }
        var filled = fill(target, scan, documentHeldBy == null);
        if (!filled.isEmpty() || documentAdded) {
            target.emailKey = Normalizer.email(target.email);
            target.documentKey = Normalizer.document(target.documentType, target.documentNumber);
            target.version++;
            target.updatedAt = clock.instant();
            if (target.salesforceState != SalesforceState.REMOVED
                    && target.salesforceState != SalesforceState.ANONYMIZED && target.status != CustomerStatus.MERGED) {
                // To Salesforce as any change of the golden record: the contact gets it, no Case. A document
                // added beside the main one goes too — the contact lists every document (HLA CM-F15).
                target.salesforceState = SalesforceState.PENDING;
            }
            customers.save(target);
            // The hotels hold copies — the front office's kardex, Opera's profiles: they learn it as always.
            events.changed(target, true, null, null, null);
        }

        if (numberKey.equals(Normalizer.documentNumber(target.documentNumber)) && target.documentVerifiedAt == null) {
            // The document it holds is the one the desk just read: «Verificado (documento)» in Salesforce,
            // with the next projection or, if nothing else changed, the next marking (ContactMarking).
            target.documentVerifiedAt = clock.instant();
            customers.save(target);
        }

        var contradictions = contradictions(target, scan, numberKey, documentHeldBy);
        String requestId = null;
        if (!contradictions.isEmpty()) {
            var origin = (scan.origin() == null ? "front office" : scan.origin()) + " · documento escaneado"
                    + (documentHeldBy == null ? "" : " (el documento ya es de " + documentHeldBy + ")");
            // The document is proposed only when it is in doubt; one simply added is no change to decide.
            var proposeDocument = contradictions.contains("documento");
            var request = changeRequests.submitUnlessPending(target.id, new ChangeRequests.Proposal(
                    scan.firstName(), scan.lastName(), null, null, null, scan.nationality(), scan.birthDate(),
                    proposeDocument ? scan.documentType() : null, proposeDocument ? scan.documentNumber() : null, origin));
            requestId = request.id;
        }
        log.info("{}/{} pax {} scanned: {} ({}); filled {}{}{}{}", scan.hotelCode(), scan.locator(), scan.pax(), target.id,
                matchedBy, filled, documentAdded ? "; document added" : "",
                absorbedId == null ? "" : "; consolidated " + absorbedId + " into it",
                requestId == null ? "" : "; contradicts " + contradictions + " → " + requestId);
        return new Outcome(target.id, matchedBy, filled, absorbedId, requestId, contradictions);
    }

    /** The customer the desk confirmed the pax is — its survivor — or null if none was, or it is unknown here. */
    Customer confirmedCustomer(RecordScannedIdentity scan) {
        var id = scan.confirmedCustomerId();
        if (blank(id)) {
            return null;
        }
        if (!customers.existsById(id)) {
            log.warn("{}/{} pax {}: the desk confirmed customer {}, which this MDM does not have: ignored",
                    scan.hotelCode(), scan.locator(), scan.pax(), id);
            return null;
        }
        return resolution.survivorOf(id);
    }

    /**
     * Which customer the scanned pax is: the one the hotel names; else one the desk scanned for that pax
     * before; else, among the reservation's passengers, the holder (pax 1) or the companion with the
     * document's name — or in the pax's place, the holder left out, as the front office lists them.
     */
    Customer paxCustomer(RecordScannedIdentity scan) {
        if (scan.customerId() != null && !scan.customerId().isBlank() && customers.existsById(scan.customerId())) {
            return resolution.survivorOf(scan.customerId());
        }
        if (scan.locator() == null || scan.locator().isBlank()) {
            return null;
        }
        var scanned = sources.findById(Source.key(scan.hotelCode(), scan.locator(), SCANNED_PAX + scan.pax()));
        if (scanned.isPresent()) {
            return resolution.survivorOf(scanned.get().customerId);
        }
        var passengers = sources.findByHotelCodeAndLocatorOrderByPassengerAsc(scan.hotelCode(), scan.locator());
        if (passengers.isEmpty()) {
            return null;
        }
        var holder = passengers.stream().filter(s -> s.passenger == 0).findFirst()
                .map(s -> resolution.survivorOf(s.customerId)).orElse(null);
        if (scan.pax() <= 1) {
            return holder;
        }
        var companions = new LinkedHashMap<String, Customer>();
        passengers.stream().filter(s -> s.passenger > 0 && s.passenger < SCANNED_PAX)
                .map(s -> resolution.survivorOf(s.customerId))
                .filter(c -> holder == null || !c.id.equals(holder.id))
                .forEach(c -> companions.putIfAbsent(c.id, c));
        var name = Normalizer.name(scan.firstName(), scan.lastName());
        var byName = companions.values().stream()
                .filter(c -> name != null && name.equals(Normalizer.name(c.firstName, c.lastName)))
                .findFirst();
        if (byName.isPresent()) {
            return byName.get();
        }
        var inPlace = new ArrayList<>(companions.values());
        return scan.pax() - 2 < inPlace.size() ? inPlace.get(scan.pax() - 2) : null;
    }

    /** A pax the reservation did not list, scanned at the desk: its lineage, so a rescan finds it. */
    void link(RecordScannedIdentity scan, Customer customer) {
        if (scan.locator() == null || scan.locator().isBlank()) {
            return;
        }
        var source = new Source();
        source.sourceKey = Source.key(scan.hotelCode(), scan.locator(), SCANNED_PAX + scan.pax());
        source.customerId = customer.id;
        source.firstCustomerId = customer.id;
        source.hotelCode = scan.hotelCode();
        source.locator = scan.locator();
        source.passenger = SCANNED_PAX + scan.pax();
        source.firstSeen = clock.instant();
        source.lastSeen = clock.instant();
        sources.save(source);
    }

    /**
     * The pax is that customer — the document's holder, or the one the desk confirmed: the survivorship of a
     * merge in Salesforce, with no steward's values — the survivor keeps its data and takes what it lacked
     * from the pax — and the two contacts to be merged in Salesforce as well, if the pax had one.
     *
     * @param via SCAN or DESK_CONFIRMED: who decided it, on the consolidation
     */
    void consolidate(Customer survivor, Customer pax, String via, String why) {
        var absorbedContact = pax.salesforceContactId;
        survivorship.merged(survivor.id, pax.id, survivor.salesforceContactId, absorbedContact,
                JsonNodeFactory.instance.objectNode(), via);
        var consolidation = consolidations.findById(pax.id).orElseThrow();
        consolidation.salesforceMerge = absorbedContact == null ? "NOT_NEEDED" : "PENDING";
        consolidation.detail = cut(why + consolidation.detail, 1000);
        consolidations.save(consolidation);
    }

    /**
     * The pax has no main document, or it is the scanned one. A pax with another main document of its own is
     * not certainly the holder — whatever other documents it has.
     */
    static boolean sameDocumentOrNone(Customer pax, String numberKey) {
        var main = Normalizer.documentNumber(pax.documentNumber);
        return main == null || main.equals(numberKey);
    }

    /** The document names the person the MDM already knows by it — however it is spelled. */
    static boolean sameName(Customer holder, RecordScannedIdentity scan) {
        var known = Normalizer.name(holder.firstName, holder.lastName);
        var scanned = Normalizer.name(scan.firstName(), scan.lastName());
        return known == null || scanned == null || known.equals(scanned);
    }

    /** What the customer lacks, from the document. */
    static List<String> fill(Customer c, RecordScannedIdentity scan, boolean withDocument) {
        var filled = new ArrayList<String>();
        if (blank(c.firstName) && blank(c.lastName) && !(blank(scan.firstName()) && blank(scan.lastName()))) {
            c.firstName = scan.firstName();
            c.lastName = scan.lastName();
            filled.add("nombre");
        }
        if (withDocument && blank(c.documentNumber)) {
            c.documentType = scan.documentType();
            c.documentNumber = scan.documentNumber();
            filled.add("documento");
        }
        if (c.birthDate == null && scan.birthDate() != null) {
            c.birthDate = scan.birthDate();
            filled.add("fecha de nacimiento");
        }
        if (blank(c.nationality) && !blank(scan.nationality())) {
            c.nationality = scan.nationality();
            filled.add("nacionalidad");
        }
        return filled;
    }

    /** What the document says otherwise than the customer: for Salesforce to decide. */
    static List<String> contradictions(Customer c, RecordScannedIdentity scan, String numberKey, String documentHeldBy) {
        var said = new ArrayList<String>();
        var known = Normalizer.name(c.firstName, c.lastName);
        var scanned = Normalizer.name(scan.firstName(), scan.lastName());
        if (known != null && scanned != null && !known.equals(scanned)) {
            said.add("nombre");
        }
        if (c.birthDate != null && scan.birthDate() != null && !c.birthDate.equals(scan.birthDate())) {
            said.add("fecha de nacimiento");
        }
        // Another document than the main one is not a contradiction — it is added beside it. Only one that is
        // someone else's, and was not the customer's already, is in doubt.
        if (documentHeldBy != null && !Objects.equals(Normalizer.documentNumber(c.documentNumber), numberKey)) {
            said.add("documento");
        }
        return said;
    }

    Customer provisional() {
        var c = new Customer();
        c.id = "C-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        c.status = CustomerStatus.PROVISIONAL;
        c.createdAt = clock.instant();
        c.updatedAt = clock.instant();
        c.salesforceState = SalesforceState.PENDING;
        return c;
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static String cut(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }
}
