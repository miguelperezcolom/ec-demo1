package io.mateu.ecdemo1.booking.infra.out.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, Long> {

    /**
     * The oldest unpublished messages, locked. {@code skip locked} lets a second replica take the
     * next ones instead of waiting — though then the two could publish one aggregate's messages out
     * of order, which is why this service runs a single replica.
     */
    @Query(value = """
            select * from outbox_message
            where published_at is null
            order by seq
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxMessageEntity> lockPending(@Param("limit") int limit);

    @Modifying
    @Query("delete from OutboxMessageEntity m where m.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
