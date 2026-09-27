package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The nights a booking covers: from the arrival date up to, not including, the departure date.
 */
public record Stay(LocalDate arrival, LocalDate departure) {

    public Stay {
        if (arrival == null || departure == null) {
            throw new IllegalArgumentException("A stay needs an arrival and a departure date");
        }
        if (!departure.isAfter(arrival)) {
            throw new IllegalArgumentException(
                    "Departure (%s) must be after arrival (%s)".formatted(departure, arrival));
        }
    }

    public int nights() {
        return (int) ChronoUnit.DAYS.between(arrival, departure);
    }

    /** One date per night, the date the night starts on. */
    public List<LocalDate> nightDates() {
        return arrival.datesUntil(departure).toList();
    }
}
