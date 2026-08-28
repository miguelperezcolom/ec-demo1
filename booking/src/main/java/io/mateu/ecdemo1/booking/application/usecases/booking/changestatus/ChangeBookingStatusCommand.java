package io.mateu.ecdemo1.booking.application.usecases.booking.changestatus;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;

public record ChangeBookingStatusCommand(String id, BookingStatus status, String taskExecutionId,
                                         String processId) {
}
