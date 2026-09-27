package io.mateu.ecdemo1.audit.store;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/** Records are added and read; the repository offers no way to change or delete one. */
public interface AuditRecordRepository extends Repository<AuditRecord, String>, JpaSpecificationExecutor<AuditRecord> {

    AuditRecord save(AuditRecord record);

    boolean existsById(String actionId);

    Optional<AuditRecord> findById(String actionId);
}
