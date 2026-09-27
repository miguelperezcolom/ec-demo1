package io.mateu.ecdemo1.mdm.change;

import io.mateu.ecdemo1.mdm.store.Xref;
import io.mateu.ecdemo1.mdm.store.XrefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Records where a customer is known: once per system and reference, refreshed when seen again. */
@Component
@RequiredArgsConstructor
public class Xrefs {

    final XrefRepository xrefs;
    final Clock clock;
    final org.springframework.transaction.PlatformTransactionManager transactions;

    /**
     * In a transaction of its own, and never failing the caller's: two callers recording the same
     * reference at once is one reference, not an error.
     */
    public void record(String customerId, Xref.Target target, String reference, String context) {
        if (customerId == null || reference == null || reference.isBlank()) {
            return;
        }
        try {
            var apart = new org.springframework.transaction.support.TransactionTemplate(transactions);
            apart.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            apart.executeWithoutResult(s -> save(customerId, target, reference, context));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Recorded by someone else meanwhile: it is there.
        }
    }

    void save(String customerId, Xref.Target target, String reference, String context) {
        var id = Xref.key(customerId, target.name(), reference);
        var xref = xrefs.findById(id).orElseGet(Xref::new);
        xref.id = id;
        xref.customerId = customerId;
        xref.system = target.name();
        xref.reference = reference;
        if (context != null) {
            xref.context = context;
        }
        xref.seenAt = clock.instant();
        xrefs.save(xref);
    }
}
