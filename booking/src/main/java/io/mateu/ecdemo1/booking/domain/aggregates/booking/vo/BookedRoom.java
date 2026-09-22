package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * One room of a booking, with the CRS's own codes and the price of each night already worked out.
 *
 * <p>{@code line} numbers the rooms of a booking from 1 and is how a room is referred to from
 * outside it.
 */
public record BookedRoom(int line,
                         String roomTypeCode,
                         String ratePlanCode,
                         String boardCode,
                         int adults,
                         List<Integer> childrenAges,
                         List<Guest> guests,
                         List<NightlyRate> nightlyRates) {

    public BookedRoom {
        if (line < 1) {
            throw new IllegalArgumentException("Room lines start at 1");
        }
        if (isBlank(roomTypeCode) || isBlank(ratePlanCode) || isBlank(boardCode)) {
            throw new IllegalArgumentException(
                    "Room %d needs a room type, a rate plan and a board".formatted(line));
        }
        if (adults < 1) {
            throw new IllegalArgumentException("Room %d needs at least one adult".formatted(line));
        }
        childrenAges = childrenAges == null ? List.of() : List.copyOf(childrenAges);
        guests = guests == null ? List.of() : List.copyOf(guests);
        nightlyRates = nightlyRates == null ? List.of() : List.copyOf(nightlyRates);
        long adultGuests = guests.stream().filter(g -> g.type() == GuestType.Adult).count();
        long childGuests = guests.stream().filter(g -> g.type() == GuestType.Child).count();
        if (adultGuests > adults || childGuests > childrenAges.size()) {
            throw new IllegalArgumentException(
                    "Room %d has more guests than its occupancy (%d adults, %d children)"
                            .formatted(line, adults, childrenAges.size()));
        }
        if (nightlyRates.isEmpty()) {
            throw new IllegalArgumentException("Room %d has no nightly rates".formatted(line));
        }
    }

    public int children() {
        return childrenAges.size();
    }

    public BigDecimal total() {
        return nightlyRates.stream().map(NightlyRate::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
