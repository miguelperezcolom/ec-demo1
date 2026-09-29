package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerNoticeRepository extends JpaRepository<CustomerNotice, String> {

    Optional<CustomerNotice> findFirstBySalesforceId(String salesforceId);

    List<CustomerNotice> findByCustomerIdInOrderByRequestedAtDesc(Collection<String> customerIds);

    List<CustomerNotice> findByCustomerId(String customerId);

    List<CustomerNotice> findTop200BySyncOrderByRequestedAtAsc(String sync);
}
