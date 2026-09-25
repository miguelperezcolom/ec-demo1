package io.mateu.ecdemo1.mapping.store;

import io.mateu.ecdemo1.integration.model.mapping.CauseType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CauseRecordRepository extends JpaRepository<CauseRecord, String> {

    List<CauseRecord> findByStatusOrderByOpenedAtAsc(CauseStatus status);

    List<CauseRecord> findByTypeAndStatus(CauseType type, CauseStatus status);
}
