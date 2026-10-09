package io.mateu.ecdemo1.loyalty.store;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String>, JpaSpecificationExecutor<Member> {

    /** A customer's membership: the most recently updated, should a merge have left it two. */
    Optional<Member> findFirstByCustomerCodeOrderByUpdatedAtDesc(String customerCode);

    List<Member> findByCustomerCode(String customerCode);

    List<Member> findByTier(String tier, Pageable page);
}
