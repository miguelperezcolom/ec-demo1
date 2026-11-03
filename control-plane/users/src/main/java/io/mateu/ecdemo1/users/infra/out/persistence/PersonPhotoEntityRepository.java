package io.mateu.ecdemo1.users.infra.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonPhotoEntityRepository extends JpaRepository<PersonPhotoEntity, String> {

    void deleteByUserId(String userId);
}
