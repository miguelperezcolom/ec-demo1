package io.mateu.ecdemo1.customerhistory.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CustomerStayRepository extends JpaRepository<CustomerStay, String> {

    /** Every row of these codes, newest departure first — on the (customerId, departure desc) index. */
    List<CustomerStay> findByCustomerIdInOrderByDepartureDescStayIdAsc(Collection<String> customerIds);

    @Modifying
    @Query("delete from CustomerStay s where s.customerId = ?1 and s.source = ?2")
    int deleteByCustomerIdAndSource(String customerId, String source);
}
