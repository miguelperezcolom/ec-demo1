package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface XrefRepository extends JpaRepository<Xref, String> {

    List<Xref> findByCustomerIdOrderBySystemAscReferenceAsc(String customerId);
}
