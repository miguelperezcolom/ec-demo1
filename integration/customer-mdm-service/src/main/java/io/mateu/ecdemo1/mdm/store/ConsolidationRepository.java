package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsolidationRepository extends JpaRepository<Consolidation, String> {

    List<Consolidation> findBySurvivorIdIsNotNullAndPropagatedAtIsNullOrderByReceivedAtAsc();

    List<Consolidation> findAllByOrderByReceivedAtDesc();
}
