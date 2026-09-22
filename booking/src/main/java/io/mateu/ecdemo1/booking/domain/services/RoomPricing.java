package io.mateu.ecdemo1.booking.domain.services;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.NightlyRate;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Stay;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Prices a room night by night, the way the CRS would: the CRS owns the commercial condition and
 * works out the full breakdown, and the PMS only records it.
 *
 * <p>The rules are simple on purpose, but they make the nights differ — a Friday or Saturday night
 * costs more — so that a daily breakdown is not a total divided by the number of nights, and a
 * projection that got it wrong would show.
 */
public class RoomPricing {

    static final BigDecimal WEEKEND_SURCHARGE = new BigDecimal("1.15");
    static final BigDecimal CHILD_BOARD_SHARE = new BigDecimal("0.5");
    static final int INFANT_MAX_AGE = 1;

    public List<NightlyRate> price(CrsCatalog.RoomType roomType, CrsCatalog.RatePlan ratePlan,
                                   CrsCatalog.Board board, int adults, List<Integer> childrenAges,
                                   Stay stay) {
        int occupancy = adults + childrenAges.size();
        if (occupancy > roomType.maxOccupancy()) {
            throw new IllegalArgumentException("Room type %s holds at most %d people, not %d"
                    .formatted(roomType.code(), roomType.maxOccupancy(), occupancy));
        }
        long payingChildren = childrenAges.stream().filter(age -> age > INFANT_MAX_AGE).count();
        var boardPerNight = board.supplementPerAdult().multiply(BigDecimal.valueOf(adults))
                .add(board.supplementPerAdult().multiply(CHILD_BOARD_SHARE)
                        .multiply(BigDecimal.valueOf(payingChildren)));
        return stay.nightDates().stream()
                .map(date -> new NightlyRate(date, roomPart(roomType, ratePlan, date).add(boardPerNight)
                        .setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    private BigDecimal roomPart(CrsCatalog.RoomType roomType, CrsCatalog.RatePlan ratePlan, LocalDate night) {
        var price = roomType.basePrice().multiply(ratePlan.factor());
        return isWeekendNight(night) ? price.multiply(WEEKEND_SURCHARGE) : price;
    }

    private static boolean isWeekendNight(LocalDate night) {
        return night.getDayOfWeek() == DayOfWeek.FRIDAY || night.getDayOfWeek() == DayOfWeek.SATURDAY;
    }
}
