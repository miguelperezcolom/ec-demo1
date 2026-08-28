package io.mateu.ecdemo1.iacp.infra.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmEntityRepository extends JpaRepository<LlmEntity, String> {
    Page<LlmEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    long countByEnabledTrue();
}
