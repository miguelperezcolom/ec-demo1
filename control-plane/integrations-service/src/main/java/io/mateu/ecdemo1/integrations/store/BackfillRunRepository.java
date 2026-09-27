package io.mateu.ecdemo1.integrations.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackfillRunRepository extends JpaRepository<BackfillRun, String> {

    List<BackfillRun> findByStatus(BackfillRun.Status status);

    Optional<BackfillRun> findFirstByIntegrationIdOrderByStartedAtDesc(String integrationId);
}
