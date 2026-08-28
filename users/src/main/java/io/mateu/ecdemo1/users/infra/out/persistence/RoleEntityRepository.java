package io.mateu.ecdemo1.users.infra.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleEntityRepository extends JpaRepository<RoleEntity, String> {
    Page<RoleEntity> findAllByNameContainingIgnoreCase(String searchText, Pageable pageable);
}
