package io.mateu.ecdemo1.mdm.store;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    List<Customer> findByEmailKeyAndStatusIn(String emailKey, Collection<CustomerStatus> statuses);

    List<Customer> findByDocumentKeyAndStatusIn(String documentKey, Collection<CustomerStatus> statuses);

    List<Customer> findByAliasOf(String survivorId);

    List<Customer> findTop50BySalesforceStateOrderByUpdatedAtAsc(SalesforceState state);

    List<Customer> findAllByOrderByUpdatedAtDesc();
}
