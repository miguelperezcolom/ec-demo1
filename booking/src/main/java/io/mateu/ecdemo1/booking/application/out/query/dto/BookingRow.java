package io.mateu.ecdemo1.booking.application.out.query.dto;

import io.mateu.uidl.data.Status;

public record BookingRow(String id,
                         String hotel,
                         String holder,
                         String arrival,
                         String departure,
                         String total,
                         Status status,
                         long version,
                         String pmsReservationId) {
}
