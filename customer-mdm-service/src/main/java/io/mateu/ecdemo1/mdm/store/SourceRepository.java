package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, String> {

    List<Source> findByCustomerIdOrderByFirstSeenAsc(String customerId);

    long countByCustomerId(String customerId);
}
