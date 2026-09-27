package io.mateu.ecdemo1.mapping.store;

import io.mateu.ecdemo1.integration.model.mapping.CauseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CauseRecordRepository extends JpaRepository<CauseRecord, String> {

    List<CauseRecord> findByStatusOrderByOpenedAtAsc(CauseStatus status);

    List<CauseRecord> findByTypeAndStatus(CauseType type, CauseStatus status);

    /** Open ones first, oldest first. {@code pattern} is a lower-case LIKE pattern escaped with '\'. */
    @Query(value = """
            select c from CauseRecord c
            where lower(c.causeKey) like :pattern escape '\\'
            order by case when c.status = 'OPEN' then 0 else 1 end, c.openedAt, c.causeKey
            """, countQuery = """
            select count(c) from CauseRecord c
            where lower(c.causeKey) like :pattern escape '\\'
            """)
    Page<CauseRecord> search(@Param("pattern") String pattern, Pageable pageable);
}
