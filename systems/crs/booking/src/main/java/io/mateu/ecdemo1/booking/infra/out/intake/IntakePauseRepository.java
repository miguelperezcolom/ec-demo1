package io.mateu.ecdemo1.booking.infra.out.intake;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IntakePauseRepository extends JpaRepository<IntakePauseEntity, String> {
}
