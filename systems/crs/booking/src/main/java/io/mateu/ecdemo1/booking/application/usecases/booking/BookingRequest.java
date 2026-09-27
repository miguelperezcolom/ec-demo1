package io.mateu.ecdemo1.booking.application.usecases.booking;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;

import java.time.LocalDate;
import java.util.List;

/**
 * A booking as someone asks for it: the CRS's codes and the occupancy, without prices — the CRS
 * works the prices out. Creating and modifying take the same request, because a modification
 * replaces the booking's terms as a whole.
 */
public record BookingRequest(String channelCode,
                             String partnerCode,
                             String externalReference,
                             LocalDate arrival,
                             LocalDate departure,
                             Holder holder,
                             List<RoomRequest> rooms,
                             String comments) {
}
