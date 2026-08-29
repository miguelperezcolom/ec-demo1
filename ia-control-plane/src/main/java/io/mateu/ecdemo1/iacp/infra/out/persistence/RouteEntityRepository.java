package io.mateu.ecdemo1.iacp.infra.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteEntityRepository extends JpaRepository<RouteEntity, String> {
    Page<RouteEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    long countByEnabledTrue();

    /** Enabled routes, tried in priority order — the read the resolver does on every routed prompt. */
    List<RouteEntity> findByEnabledTrueOrderByPriorityAsc();
}
