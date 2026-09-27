package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, String>, JpaSpecificationExecutor<ChangeRequest> {

    List<ChangeRequest> findByStatusOrderByRequestedAtAsc(String status);

    List<ChangeRequest> findByCustomerIdOrderByRequestedAtDesc(String customerId);

    List<ChangeRequest> findByCustomerIdInOrderByRequestedAtDesc(java.util.Collection<String> customerIds);

    List<ChangeRequest> findAllByOrderByRequestedAtDesc();
}
