package io.mateu.ecdemo1.iacp.infra.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEntityRepository extends JpaRepository<AgentEntity, String> {
    Page<AgentEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    long countByEnabledTrue();
}
