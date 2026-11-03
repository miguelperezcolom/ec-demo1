package io.mateu.ecdemo1.users.infra.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEntityRepository extends JpaRepository<UserEntity, String> {
    Page<UserEntity> findAllByNameContainingIgnoreCase(String name, Pageable pageable);

    /** By what someone looking for a person may type: their name, their username or their email. */
    Page<UserEntity> findAllByNameContainingIgnoreCaseOrIdContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String id,
                                                                                                         String email, Pageable pageable);
}
