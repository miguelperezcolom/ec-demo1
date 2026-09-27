package io.mateu.ecdemo1.mdm.scan;

import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * A consolidation the MDM decided itself — a scanned document was already another customer's — is
 * also made in Salesforce: the absorbed customer's contact is merged into the survivor's, as a steward
 * would, so Salesforce is not left with a duplicate the MDM has already settled. It waits for the
 * survivor's contact if it is not there yet. Salesforce then announces the merge (ClienteConsolidado__e)
 * like any other; the MDM finds it applied already and does nothing more.
 *
 * <p>If Salesforce refuses — the absorbed contact is gone, or was merged by hand meanwhile — it is
 * recorded on the consolidation and not retried: the MDM is already right, and what is left in
 * Salesforce is a duplicate its rules flag for a steward.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SalesforceMerges {

    final SalesforceClient salesforce;
    final ConsolidationRepository consolidations;
    final CustomerRepository customers;
    final TransactionTemplate tx;
    final Clock clock;
    io.mateu.ecdemo1.mdm.salesforce.Backoff backoff;

    io.mateu.ecdemo1.mdm.salesforce.Backoff backoff() {
        if (backoff == null) {
            backoff = new io.mateu.ecdemo1.mdm.salesforce.Backoff(clock, java.time.Duration.ofSeconds(10), java.time.Duration.ofMinutes(10));
        }
        return backoff;
    }

    @Scheduled(fixedDelayString = "${mdm.salesforce-merge-tick:10s}")
    public void mergePending() {
        if (!salesforce.available() || !backoff().ready()) {
            return;
        }
        for (var pending : consolidations.findBySalesforceMergeOrderByReceivedAtAsc("PENDING")) {
            var survivor = customers.findById(pending.survivorId).orElse(null);
            var absorbed = customers.findById(pending.absorbedId).orElse(null);
            var absorbedContact = absorbed != null && absorbed.salesforceContactId != null
                    ? absorbed.salesforceContactId : pending.absorbedContactId;
            if (survivor == null || absorbedContact == null) {
                mark(pending.absorbedId, "NOT_NEEDED", null);
                continue;
            }
            if (survivor.salesforceContactId == null) {
                // The survivor is not a contact yet: the projection makes it one, and then this goes.
                continue;
            }
            if (survivor.salesforceContactId.equals(absorbedContact)) {
                mark(pending.absorbedId, "NOT_NEEDED", null);
                continue;
            }
            try {
                salesforce.mergeContacts(survivor.salesforceContactId, absorbedContact);
                mark(pending.absorbedId, "DONE", null);
                backoff().succeeded();
                log.info("{}'s contact {} merged into {}'s ({}) in Salesforce", pending.absorbedId, absorbedContact,
                        survivor.id, survivor.salesforceContactId);
            } catch (SalesforceClient.MergeRefused e) {
                log.warn("Salesforce refused to merge {} into {}: {} — the duplicate is left for a steward",
                        absorbedContact, survivor.salesforceContactId, e.getMessage());
                mark(pending.absorbedId, "FAILED", e.getMessage());
            } catch (SalesforceClient.LimitExceeded e) {
                return;
            } catch (RuntimeException e) {
                backoff().failed();
                log.warn("Salesforce unreachable, the merge of {} waits until {}: {}", pending.absorbedId, backoff().next(), e.getMessage());
                return;
            }
        }
    }

    void mark(String absorbedId, String state, String error) {
        tx.executeWithoutResult(s -> consolidations.findById(absorbedId).ifPresent(c -> {
            c.salesforceMerge = state;
            c.salesforceMergeError = error == null ? null : error.length() > 1000 ? error.substring(0, 999) + "…" : error;
            if ("DONE".equals(state)) {
                c.salesforceMergedAt = clock.instant();
            }
            consolidations.save(c);
        }));
    }
}
