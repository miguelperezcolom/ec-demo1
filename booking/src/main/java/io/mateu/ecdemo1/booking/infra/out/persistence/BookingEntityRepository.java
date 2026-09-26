package io.mateu.ecdemo1.booking.infra.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingEntityRepository extends JpaRepository<BookingEntity, String>,
        JpaSpecificationExecutor<BookingEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.id = :id")
    Optional<BookingEntity> findByIdForUpdate(@Param("id") String id);

    @Query("""
            select b from BookingEntity b
            where :text is null or :text = ''
               or lower(b.id) like lower(concat('%', :text, '%'))
               or lower(b.holderName) like lower(concat('%', :text, '%'))
               or lower(b.hotelCode) like lower(concat('%', :text, '%'))
            order by b.created desc
            """)
    Page<BookingEntity> search(@Param("text") String text, Pageable pageable);

    /**
     * A hotel's bookings still to arrive, not cancelled, in arrival order — the order a backfill
     * projects them in — resumed after the last one a previous page returned.
     */
    @Query("""
            select b from BookingEntity b
            where b.hotelCode = :hotel and b.arrival >= :from and b.status <> 'Cancelled'
              and (b.arrival > :afterArrival or (b.arrival = :afterArrival and b.id > :afterId))
            order by b.arrival, b.id
            """)
    List<BookingEntity> future(@Param("hotel") String hotel, @Param("from") LocalDate from,
                               @Param("afterArrival") LocalDate afterArrival, @Param("afterId") String afterId,
                               Pageable pageable);
}
