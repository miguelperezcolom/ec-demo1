package io.mateu.ecdemo1.integrations.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FoBackfillRunRepository extends JpaRepository<FoBackfillRun, String> {

    List<FoBackfillRun> findByStatus(FoBackfillRun.Status status);

    Optional<FoBackfillRun> findFirstByIntegrationIdOrderByStartedAtDesc(String integrationId);
}
