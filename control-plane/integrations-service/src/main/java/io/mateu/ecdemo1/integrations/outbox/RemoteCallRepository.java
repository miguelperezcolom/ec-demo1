package io.mateu.ecdemo1.integrations.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RemoteCallRepository extends JpaRepository<RemoteCallEntity, Long> {

    /** The calls due now that nobody is sending, oldest first. */
    @Query(value = """
            select id from remote_call
            where done_at is null and failed_at is null and next_attempt_at <= :now
              and (claimed_until is null or claimed_until < :now)
            order by id
            limit :limit
            """, nativeQuery = true)
    List<Long> due(@Param("now") Instant now, @Param("limit") int limit);

    /** Takes the call for sending until {@code until}: 1 if it was free to take, 0 if not. */
    @Modifying
    @Query("""
            update RemoteCallEntity c set c.claimedUntil = :until
            where c.id = :id and c.doneAt is null and c.failedAt is null
              and (c.claimedUntil is null or c.claimedUntil < :now)
            """)
    int claim(@Param("id") Long id, @Param("now") Instant now, @Param("until") Instant until);

    @Modifying
    @Query("delete from RemoteCallEntity c where c.doneAt < :before")
    int deleteDoneBefore(@Param("before") Instant before);

    long countByDoneAtIsNullAndFailedAtIsNull();
}
