package io.mateu.ecdemo1.iacp.infra.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiMcpEntityRepository extends JpaRepository<ApiMcpEntity, String> {

    Page<ApiMcpEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    long countByEnabledTrue();
}
