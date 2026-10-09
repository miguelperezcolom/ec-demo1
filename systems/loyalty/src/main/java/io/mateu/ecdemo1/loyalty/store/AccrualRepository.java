package io.mateu.ecdemo1.loyalty.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AccrualRepository extends JpaRepository<Accrual, String>, JpaSpecificationExecutor<Accrual> {

    List<Accrual> findByMemberNumberOrderByAtDesc(String memberNumber);
}
