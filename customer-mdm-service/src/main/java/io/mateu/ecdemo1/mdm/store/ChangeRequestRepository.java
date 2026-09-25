package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, String> {

    List<ChangeRequest> findByStatusOrderByRequestedAtAsc(String status);

    List<ChangeRequest> findByCustomerIdOrderByRequestedAtDesc(String customerId);
}
