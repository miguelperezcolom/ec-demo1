package io.mateu.ecdemo1.mdm.salesforce;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;

/**
 * «Proyectar a Salesforce» (HLA CRM-MDM, Ciclo de Limpieza): what is pending goes to Salesforce as a
 * contact, where its duplicate rules find it and a steward merges it. Salesforce is a worker: when it
 * is down the customers wait here, and the sale has long gone on with their provisional codes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Projection {

    final SalesforceClient salesforce;
    final CustomerRepository customers;
    final TransactionTemplate tx;
    final io.mateu.ecdemo1.mdm.change.Xrefs xrefs;
    final Clock clock;

    @Scheduled(fixedDelayString = "${mdm.projection-tick:5s}")
    public void projectPending() {
        if (!salesforce.enabled()) {
            return;
        }
        for (var pending : customers.findTop50BySalesforceStateOrderByUpdatedAtAsc(SalesforceState.PENDING)) {
            if (pending.status == CustomerStatus.MERGED) {
                tx.executeWithoutResult(s -> customers.findById(pending.id).ifPresent(c -> {
                    c.salesforceState = SalesforceState.NOT_PROJECTED;
                    customers.save(c);
                }));
                continue;
            }
            var sent = pending.version;
            try {
                var contactId = salesforce.upsertContact(pending);
                tx.executeWithoutResult(s -> customers.findById(pending.id).ifPresent(c -> {
                    c.salesforceContactId = contactId;
                    c.projectedAt = clock.instant();
                    c.projectionError = null;
                    // Changed while it was being sent: stays pending, and goes again.
                    if (c.version == sent) {
                        c.salesforceState = SalesforceState.PROJECTED;
                    }
                    customers.save(c);
                }));
                xrefs.record(pending.id, io.mateu.ecdemo1.mdm.store.Xref.Target.SALESFORCE, contactId, null);
                log.info("{} projected to Salesforce as {}", pending.id, contactId);
            } catch (HttpClientErrorException e) {
                // Salesforce refused this one: say why on the record, and go on with the rest.
                log.warn("Salesforce refused {}: {}", pending.id, e.getResponseBodyAsString());
                tx.executeWithoutResult(s -> customers.findById(pending.id).ifPresent(c -> {
                    c.projectionError = e.getStatusCode().value() + " " + e.getResponseBodyAsString();
                    if (c.version == sent) {
                        c.salesforceState = SalesforceState.FAILED;
                    }
                    customers.save(c);
                }));
            } catch (RuntimeException e) {
                log.warn("Salesforce unreachable, projection waits: {}", e.getMessage());
                return;
            }
        }
    }
}
