package io.mateu.ecdemo1.booking.infra.out.persistence;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingTerms;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Cancellation;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PmsReference;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Stay;

import java.util.List;

final class BookingMapper {

    private BookingMapper() {
    }

    static Booking toDomain(BookingEntity entity) {
        return new Booking(
                new BookingId(entity.id),
                entity.hotelCode,
                entity.currency,
                new BookingTerms(
                        entity.channelCode,
                        entity.partnerCode,
                        entity.externalReference,
                        new Stay(entity.arrival, entity.departure),
                        entity.holder,
                        entity.rooms,
                        entity.comments),
                entity.payments != null ? entity.payments : List.of(),
                BookingStatus.valueOf(entity.status),
                entity.cancellationReason != null
                        ? new Cancellation(entity.cancellationReason, entity.cancelledAt, entity.cancellationFee,
                        entity.cancellationFeePercent) : null,
                entity.pmsReservationId != null
                        ? new PmsReference(entity.pmsReservationId, entity.pmsAnnotatedAt) : null,
                entity.created,
                entity.updated,
                entity.version);
    }

    static void copy(Booking booking, BookingEntity entity) {
        var terms = booking.getTerms();
        entity.id = booking.getId().id();
        entity.hotelCode = booking.getHotelCode();
        entity.currency = booking.getCurrency();
        entity.status = booking.getStatus().name();
        entity.version = booking.getVersion();
        entity.channelCode = terms.channelCode();
        entity.partnerCode = terms.partnerCode();
        entity.externalReference = terms.externalReference();
        entity.arrival = terms.stay().arrival();
        entity.departure = terms.stay().departure();
        entity.holderName = terms.holder().fullName();
        entity.holder = terms.holder();
        entity.rooms = terms.rooms();
        entity.payments = booking.getPayments();
        entity.comments = terms.comments();
        entity.cancellationReason = booking.getCancellation() != null ? booking.getCancellation().reasonCode() : null;
        entity.cancelledAt = booking.getCancellation() != null ? booking.getCancellation().cancelledAt() : null;
        entity.cancellationFee = booking.getCancellation() != null ? booking.getCancellation().fee() : null;
        entity.cancellationFeePercent = booking.getCancellation() != null ? booking.getCancellation().feePercent() : null;
        entity.pmsReservationId = booking.getPmsReference() != null ? booking.getPmsReference().reservationId() : null;
        entity.pmsAnnotatedAt = booking.getPmsReference() != null ? booking.getPmsReference().annotatedAt() : null;
        entity.created = booking.getCreated();
        entity.updated = booking.getUpdated();
    }
}
