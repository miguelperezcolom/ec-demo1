package io.mateu.ecdemo1.mapping.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WaiterRepository extends JpaRepository<Waiter, String> {

    @Query("""
            select w from Waiter w
            where w.status = 'WAITING'
              and exists (select 1 from WaiterCause l where l.processKey = w.processKey and l.causeKey = :cause)
            """)
    List<Waiter> waitingOn(@Param("cause") String causeKey);

    @Query("select w from Waiter w where w.status = 'RELEASED' and w.lastSignalAt < :before")
    List<Waiter> releasedAndSilentSince(@Param("before") Instant before);

    @Query("""
            select count(w) from Waiter w
            where w.status = 'WAITING'
              and exists (select 1 from WaiterCause l where l.processKey = w.processKey and l.causeKey = :cause)
            """)
    long countWaitingOn(@Param("cause") String causeKey);
}
