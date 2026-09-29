package io.mateu.ecdemo1.mdm.salesforce;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;
import java.util.ArrayList;

/**
 * «Proyectar a Salesforce» (HLA CRM-MDM, Ciclo de Limpieza): what is pending goes to Salesforce as a
 * contact, where its duplicate rules find it and a steward merges it. Salesforce is a worker: when it
 * is down the customers wait here, and the sale has long gone on with their provisional codes.
 *
 * <p>Up to {@value SalesforceClient#COLLECTION} at a time in one call — the org's daily allowance
 * counts calls, not contacts. When the allowance is spent they stay pending, not failed: nothing is
 * wrong with them, and they go when Salesforce answers again.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Projection {

    final SalesforceClient salesforce;
    final CustomerRepository customers;
    final TransactionTemplate tx;
    final io.mateu.ecdemo1.mdm.change.Xrefs xrefs;
    final io.mateu.ecdemo1.mdm.marking.Origins origins;
    final Clock clock;
    Backoff backoff;

    Backoff backoff() {
        if (backoff == null) {
            backoff = new Backoff(clock, java.time.Duration.ofSeconds(10), java.time.Duration.ofMinutes(10));
        }
        return backoff;
    }

    @Scheduled(fixedDelayString = "${mdm.projection-tick:5s}")
    public void projectPending() {
        if (!salesforce.available() || !backoff().ready()) {
            return;
        }
        var batch = new ArrayList<Customer>();
        for (var pending : customers.findTop200BySalesforceStateOrderByUpdatedAtAsc(SalesforceState.PENDING)) {
            if (pending.status == CustomerStatus.MERGED) {
                tx.executeWithoutResult(s -> customers.findById(pending.id).ifPresent(c -> {
                    c.salesforceState = SalesforceState.NOT_PROJECTED;
                    customers.save(c);
                }));
                continue;
            }
            if (pending.origin == null) {
                // Its origin goes with it: read once from its first booking (the CRS, not Salesforce).
                origins.fill(pending);
                if (pending.origin != null) {
                    var origin = pending.origin;
                    tx.executeWithoutResult(s -> customers.findById(pending.id).ifPresent(c -> {
                        c.origin = origin;
                        customers.save(c);
                    }));
                }
            }
            batch.add(pending);
        }
        if (batch.isEmpty()) {
            return;
        }
        java.util.List<SalesforceClient.Upserted> answers;
        try {
            answers = salesforce.upsertContacts(batch);
        } catch (SalesforceClient.LimitExceeded e) {
            // Nothing is wrong with them: they stay pending, and go when the allowance is back.
            log.debug("Projection waits for Salesforce's allowance: {}", e.getMessage());
            return;
        } catch (HttpClientErrorException e) {
            // The whole call refused, and sending it again would be refused again: failed, as a refused
            // customer is — each goes again when it changes.
            log.warn("Salesforce refused the projection of {} customer(s): {}", batch.size(), e.getResponseBodyAsString());
            batch.forEach(sent -> tx.executeWithoutResult(s -> customers.findById(sent.id).ifPresent(c -> {
                c.projectionError = cut(e.getStatusCode().value() + " " + e.getResponseBodyAsString());
                if (c.version == sent.version) {
                    c.salesforceState = SalesforceState.FAILED;
                }
                customers.save(c);
            })));
            return;
        } catch (RuntimeException e) {
            backoff().failed();
            log.warn("Salesforce unreachable, projection waits until {}: {}", backoff().next(), e.getMessage());
            return;
        }
        backoff().succeeded();
        for (int i = 0; i < batch.size(); i++) {
            var pending = batch.get(i);
            var answer = answers.get(i);
            var sent = pending.version;
            var marking = io.mateu.ecdemo1.mdm.marking.Marking.of(pending).key();
            if (answer.ok()) {
                tx.executeWithoutResult(s -> customers.findById(pending.id).ifPresent(c -> {
                    c.salesforceContactId = answer.contactId();
                    c.markedAs = marking;
                    c.projectedAt = clock.instant();
                    c.projectionError = null;
                    // Changed while it was being sent: stays pending, and goes again.
                    if (c.version == sent) {
                        c.salesforceState = SalesforceState.PROJECTED;
                    }
                    customers.save(c);
                }));
                xrefs.record(pending.id, io.mateu.ecdemo1.mdm.store.Xref.Target.SALESFORCE, answer.contactId(), null);
                log.info("{} projected to Salesforce as {}", pending.id, answer.contactId());
            } else {
                // Salesforce refused this one: say why on the record, and go on with the rest.
                log.warn("Salesforce refused {}: {}", pending.id, answer.error());
                tx.executeWithoutResult(s -> customers.findById(pending.id).ifPresent(c -> {
                    c.projectionError = cut(answer.error());
                    if (c.version == sent) {
                        c.salesforceState = SalesforceState.FAILED;
                    }
                    customers.save(c);
                }));
            }
        }
    }

    /**
     * Customers an earlier version marked failed when it was only the allowance refusing: they were
     * never refused for what they are, so they go again.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void requeueRefusedForTheAllowance() {
        var requeued = tx.execute(s -> {
            var refused = customers.findBySalesforceStateAndProjectionErrorContaining(SalesforceState.FAILED, "REQUEST_LIMIT_EXCEEDED");
            refused.forEach(c -> c.salesforceState = SalesforceState.PENDING);
            customers.saveAll(refused);
            return refused.size();
        });
        if (requeued != null && requeued > 0) {
            log.info("{} customer(s) failed only for Salesforce's allowance: pending again", requeued);
        }
    }

    static String cut(String value) {
        return value == null || value.length() <= 1000 ? value : value.substring(0, 999) + "…";
    }
}
