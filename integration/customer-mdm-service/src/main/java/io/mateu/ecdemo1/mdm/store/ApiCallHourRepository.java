package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface ApiCallHourRepository extends JpaRepository<ApiCallHour, String> {

    List<ApiCallHour> findByHourGreaterThanEqual(Instant since);

    @Modifying
    @Transactional
    @Query("delete from ApiCallHour h where h.hour < :before")
    int deleteOlderThan(Instant before);
}
