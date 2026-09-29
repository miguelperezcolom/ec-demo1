package io.mateu.ecdemo1.mdm.marking;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps each contact's marking ({@link Marking}) as the MDM computes it. A projection already sends it
 * with the contact; this is for what changes the marking without changing the record's data — the
 * customer consolidated, its origin told, a scan verifying the document it had — and for the contacts
 * that were in Salesforce before the marking existed (the backfill: the first run marks them all).
 *
 * <p>Only the contacts whose marking differs from the one they have, and only the three fields, up to
 * {@value SalesforceClient#COLLECTION} in one call: a run with nothing to mark calls nobody.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContactMarking {

    final SalesforceClient salesforce;
    final CustomerRepository customers;
    final Origins origins;
    final TransactionTemplate tx;

    @Scheduled(fixedDelayString = "${mdm.marking-tick:2m}", initialDelayString = "${mdm.marking-initial-delay:1m}")
    public void reconcile() {
        if (!salesforce.available()) {
            return;
        }
        var toMark = toMark();
        if (toMark.isEmpty()) {
            return;
        }
        var marked = 0;
        for (int from = 0; from < toMark.size(); from += SalesforceClient.COLLECTION) {
            var batch = toMark.subList(from, Math.min(toMark.size(), from + SalesforceClient.COLLECTION));
            List<SalesforceClient.Upserted> answers;
            try {
                answers = salesforce.markContacts(batch);
            } catch (SalesforceClient.LimitExceeded e) {
                log.debug("Marking contacts waits for Salesforce's allowance");
                return;
            } catch (RuntimeException e) {
                log.warn("Could not mark {} contact(s) in Salesforce, next time: {}", batch.size(), e.getMessage());
                return;
            }
            for (int i = 0; i < batch.size(); i++) {
                var sent = batch.get(i);
                var answer = answers.get(i);
                if (answer.ok()) {
                    var key = Marking.of(sent).key();
                    tx.executeWithoutResult(s -> customers.findById(sent.id).ifPresent(c -> {
                        c.markedAs = key;
                        customers.save(c);
                    }));
                    marked++;
                } else {
                    log.warn("Salesforce did not take the marking of {}: {}", sent.id, answer.error());
                }
            }
        }
        log.info("{} contact(s) marked in Salesforce", marked);
    }

    /** The contacts whose marking changed — their origin told first, where it was not yet. */
    List<Customer> toMark() {
        var result = new ArrayList<Customer>();
        for (var c : customers.findBySalesforceStateAndSalesforceContactIdNotNullAndAnonymizedAtIsNull(SalesforceState.PROJECTED)) {
            if (c.status == CustomerStatus.MERGED) {
                continue;
            }
            if (c.origin == null) {
                origins.fill(c);
                if (c.origin != null) {
                    var origin = c.origin;
                    tx.executeWithoutResult(s -> customers.findById(c.id).ifPresent(r -> {
                        r.origin = origin;
                        customers.save(r);
                    }));
                }
            }
            if (needsMarking(c)) {
                result.add(c);
            }
        }
        return result;
    }

    static boolean needsMarking(Customer c) {
        return !Marking.of(c).key().equals(c.markedAs);
    }
}
