package io.mateu.ecdemo1.booking.infra.out.persistence;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookedRoom;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One booking, one row. What is searched or listed has a column of its own; the nested parts —
 * holder, rooms with their guests and nightly rates, payments — are JSON, because they are only
 * ever read and written with the booking they belong to.
 *
 * <p>A table of its own, {@code crs_booking}, and not the {@code booking_entity} the old,
 * lead-name-only model used: with {@code ddl-auto: update} and no migrations there is no way to
 * give those rows the hotel, stay and rooms the new model cannot do without.
 */
@Entity
@Table(name = "crs_booking")
@NoArgsConstructor
@Getter
@Setter
public class BookingEntity {

    @Id
    String id;

    @Column(nullable = false)
    String hotelCode;

    @Column(nullable = false)
    String currency;

    @Column(nullable = false)
    String status;

    long version;

    @Column(nullable = false)
    String channelCode;

    String partnerCode;

    String externalReference;

    @Column(nullable = false)
    LocalDate arrival;

    @Column(nullable = false)
    LocalDate departure;

    /** Denormalised from the holder, for searching. */
    String holderName;

    @JdbcTypeCode(SqlTypes.JSON)
    Holder holder;

    @JdbcTypeCode(SqlTypes.JSON)
    List<BookedRoom> rooms;

    @JdbcTypeCode(SqlTypes.JSON)
    List<Payment> payments;

    @Column(length = 4000)
    String comments;

    String cancellationReason;

    Instant cancelledAt;

    /** What the cancellation costs (a no-show's fee), and the share of the price it is. */
    java.math.BigDecimal cancellationFee;

    Integer cancellationFeePercent;

    String pmsReservationId;

    Instant pmsAnnotatedAt;

    Instant created;

    Instant updated;

}
