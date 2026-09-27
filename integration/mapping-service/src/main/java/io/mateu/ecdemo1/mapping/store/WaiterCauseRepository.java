package io.mateu.ecdemo1.mapping.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WaiterCauseRepository extends JpaRepository<WaiterCause, WaiterCause.Key> {

    List<WaiterCause> findByProcessKey(String processKey);

    @Query("""
            select count(l) from WaiterCause l, CauseRecord c
            where l.processKey = :process and c.causeKey = l.causeKey and c.status = 'OPEN'
            """)
    long openCausesOf(@Param("process") String processKey);
}
