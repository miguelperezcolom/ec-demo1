package io.mateu.ecdemo1.booking.application.usecases.booking;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Guest;

import java.util.List;

public record RoomRequest(String roomTypeCode,
                          String ratePlanCode,
                          String boardCode,
                          int adults,
                          List<Integer> childrenAges,
                          List<Guest> guests) {
}
