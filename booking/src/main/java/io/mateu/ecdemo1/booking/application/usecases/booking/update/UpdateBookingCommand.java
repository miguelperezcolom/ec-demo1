package io.mateu.ecdemo1.booking.application.usecases.booking.update;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;

public record UpdateBookingCommand(String id, BookingRequest booking) {
}
