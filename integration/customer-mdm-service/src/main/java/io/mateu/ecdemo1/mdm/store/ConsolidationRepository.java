package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ConsolidationRepository extends JpaRepository<Consolidation, String>, JpaSpecificationExecutor<Consolidation> {

    List<Consolidation> findBySurvivorIdIsNotNullAndPropagatedAtIsNullOrderByReceivedAtAsc();

    List<Consolidation> findAllByOrderByReceivedAtDesc();

    List<Consolidation> findBySalesforceMergeOrderByReceivedAtAsc(String salesforceMerge);
}
