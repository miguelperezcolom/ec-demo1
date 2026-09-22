package io.mateu.ecdemo1.booking.application.usecases.booking.create;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;

public record CreateBookingCommand(String hotelCode, BookingRequest booking) {
}
