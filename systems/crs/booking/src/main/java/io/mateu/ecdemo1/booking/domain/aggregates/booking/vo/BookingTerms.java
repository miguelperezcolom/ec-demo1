package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

/**
 * Everything about a booking that a modification replaces as a whole: the commercial and stay
 * details, as opposed to its identity (id, hotel), its lifecycle (status) and its payments.
 *
 * <p>{@code partnerCode} is the trading partner — tour operator, agency, company — the booking was
 * sold through; null for a direct sale. {@code externalReference} is that partner's own reference
 * (the voucher).
 */
public record BookingTerms(String channelCode,
                           String partnerCode,
                           String externalReference,
                           Stay stay,
                           Holder holder,
                           List<BookedRoom> rooms,
                           String comments) {

    public BookingTerms {
        if (channelCode == null || channelCode.isBlank()) {
            throw new IllegalArgumentException("A booking needs a channel");
        }
        if (stay == null || holder == null) {
            throw new IllegalArgumentException("A booking needs a stay and a holder");
        }
        if (rooms == null || rooms.isEmpty()) {
            throw new IllegalArgumentException("A booking needs at least one room");
        }
        rooms = List.copyOf(rooms);
        var lines = new HashSet<Integer>();
        for (var room : rooms) {
            if (!lines.add(room.line())) {
                throw new IllegalArgumentException("Room line %d is repeated".formatted(room.line()));
            }
            var rated = room.nightlyRates().stream().map(NightlyRate::date).toList();
            if (!rated.equals(stay.nightDates())) {
                throw new IllegalArgumentException(
                        "Room %d must have exactly one rate per night of the stay".formatted(room.line()));
            }
        }
    }

    public BigDecimal total() {
        return rooms.stream().map(BookedRoom::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
