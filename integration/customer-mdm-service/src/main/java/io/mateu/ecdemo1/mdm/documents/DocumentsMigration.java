package io.mateu.ecdemo1.mdm.documents;

import io.mateu.ecdemo1.mdm.resolution.Normalizer;
import io.mateu.ecdemo1.mdm.store.CustomerDocument;
import io.mateu.ecdemo1.mdm.store.CustomerDocumentRepository;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Before customers had several documents, each had one: its main document, on the customer. On every
 * start, the main document of each live customer that is not among its documents yet is added to them —
 * so lookups by document, which read only the documents, find the customers known from before. Doing it
 * again finds nothing to do.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentsMigration implements ApplicationRunner {

    final CustomerRepository customers;
    final CustomerDocumentRepository documentRows;
    final CustomerDocuments documents;
    final TransactionTemplate transactions;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var added = migrate();
            if (added > 0) {
                log.info("{} customer(s)' main document added to their documents", added);
            }
        } catch (RuntimeException e) {
            // A start is not refused for it: it is tried again on the next one.
            log.error("The main documents could not be added to the customers' documents", e);
        }
    }

    /** How many documents it added. */
    public int migrate() {
        var added = 0;
        for (var id : documentRows.customersWithoutTheirMainDocument()) {
            Boolean done = transactions.execute(s -> {
                var c = customers.findById(id).orElse(null);
                var numberKey = c == null ? null : Normalizer.documentNumber(c.documentNumber);
                if (numberKey == null || !documentRows.findByCustomerIdAndNumberKey(c.id, numberKey).isEmpty()) {
                    return false;
                }
                documents.add(c.id, c.documentType, c.documentNumber, c.nationality, null, CustomerDocument.Origin.CUSTOMER.name());
                return true;
            });
            if (Boolean.TRUE.equals(done)) {
                added++;
            }
        }
        return added;
    }
}
