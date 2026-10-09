package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerDocumentRepository extends JpaRepository<CustomerDocument, String> {

    /** Who holds this document, issued by this country. */
    List<CustomerDocument> findByIssuingCountryAndNumberKey(String issuingCountry, String numberKey);

    /** Who holds a document with this number, whichever country issued it. */
    List<CustomerDocument> findByNumberKey(String numberKey);

    List<CustomerDocument> findByCustomerIdOrderByFirstSeenAtAsc(String customerId);

    List<CustomerDocument> findByCustomerIdAndNumberKey(String customerId, String numberKey);

    /**
     * The customers whose main document may not be among their documents yet: the ones before
     * documents were kept apart. Only a first sieve — the number is normalised here as
     * {@code Normalizer.documentNumber} does, and the migration checks each one again in Java.
     */
    @Query(value = """
            select c.id from customer c
            where c.status <> 'MERGED' and c.document_number is not null and btrim(c.document_number) <> ''
              and not exists (select 1 from customer_document d where d.customer_id = c.id
                              and d.number_key = regexp_replace(upper(c.document_number), '[^A-Z0-9]', '', 'g'))
            """, nativeQuery = true)
    List<String> customersWithoutTheirMainDocument();
}
