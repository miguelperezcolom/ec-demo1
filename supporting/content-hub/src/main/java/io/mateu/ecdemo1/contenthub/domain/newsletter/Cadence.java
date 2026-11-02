package io.mateu.ecdemo1.contenthub.domain.newsletter;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

/**
 * How often a newsletter has a new issue: never by itself, every week on a weekday, or every month on a
 * day of the month — at a time of day, Madrid time. Each of those moments is a SLOT; the scheduler opens
 * one issue per slot, and only for the latest slot that has passed (a service that was down for three
 * weeks opens one issue, not three).
 *
 * @param dayOfMonth 1–31; a month shorter than that uses its last day
 */
public record Cadence(Frequency frequency, DayOfWeek dayOfWeek, Integer dayOfMonth, LocalTime time) {

    public enum Frequency { NONE, WEEKLY, MONTHLY }

    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    public static final Cadence NONE = new Cadence(Frequency.NONE, null, null, null);
    static final LocalTime NINE = LocalTime.of(9, 0);
    static final DateTimeFormatter KEY = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZONE);

    public Cadence {
        frequency = frequency == null ? Frequency.NONE : frequency;
        time = time == null ? NINE : time.withSecond(0).withNano(0);
        if (frequency == Frequency.WEEKLY && dayOfWeek == null) {
            dayOfWeek = DayOfWeek.MONDAY;
        }
        if (frequency == Frequency.MONTHLY) {
            dayOfMonth = dayOfMonth == null ? 1 : dayOfMonth;
            if (dayOfMonth < 1 || dayOfMonth > 31) {
                throw new IllegalArgumentException("El día del mes va de 1 a 31");
            }
        }
        if (frequency != Frequency.WEEKLY) {
            dayOfWeek = null;
        }
        if (frequency != Frequency.MONTHLY) {
            dayOfMonth = null;
        }
    }

    public static Cadence weekly(DayOfWeek day, LocalTime time) {
        return new Cadence(Frequency.WEEKLY, day, null, time);
    }

    public static Cadence monthly(int day, LocalTime time) {
        return new Cadence(Frequency.MONTHLY, null, day, time);
    }

    /** The latest slot at or before {@code now}; none without a frequency. */
    public Optional<Instant> slotAtOrBefore(Instant now) {
        var today = now.atZone(ZONE).toLocalDate();
        return switch (frequency) {
            case NONE -> Optional.empty();
            case WEEKLY -> {
                var candidate = at(today.with(TemporalAdjusters.previousOrSame(dayOfWeek)));
                yield Optional.of(candidate.isAfter(now) ? at(today.with(TemporalAdjusters.previousOrSame(dayOfWeek))
                        .minusWeeks(1)) : candidate);
            }
            case MONTHLY -> {
                var month = YearMonth.from(today);
                var candidate = at(day(month));
                yield Optional.of(candidate.isAfter(now) ? at(day(month.minusMonths(1))) : candidate);
            }
        };
    }

    /** The first slot after {@code now}; none without a frequency. */
    public Optional<Instant> nextSlotAfter(Instant now) {
        var today = now.atZone(ZONE).toLocalDate();
        return switch (frequency) {
            case NONE -> Optional.empty();
            case WEEKLY -> {
                var candidate = at(today.with(TemporalAdjusters.nextOrSame(dayOfWeek)));
                yield Optional.of(candidate.isAfter(now) ? candidate
                        : at(today.with(TemporalAdjusters.nextOrSame(dayOfWeek)).plusWeeks(1)));
            }
            case MONTHLY -> {
                var month = YearMonth.from(today);
                var candidate = at(day(month));
                yield Optional.of(candidate.isAfter(now) ? candidate : at(day(month.plusMonths(1))));
            }
        };
    }

    /** How far back the first issue looks for content: a week, or a month. */
    public Duration period() {
        return frequency == Frequency.MONTHLY ? Duration.ofDays(31) : Duration.ofDays(7);
    }

    /** A slot as part of an issue's id: {@code 20261102-0900}. */
    public static String key(Instant slot) {
        return KEY.format(slot);
    }

    public String describe() {
        var es = Locale.forLanguageTag("es");
        return switch (frequency) {
            case NONE -> "Sin periodicidad: los números se crean a mano";
            case WEEKLY -> "Semanal, los " + dayOfWeek.getDisplayName(TextStyle.FULL, es) + " a las " + time;
            case MONTHLY -> "Mensual, el día " + dayOfMonth + " a las " + time;
        };
    }

    LocalDate day(YearMonth month) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    Instant at(LocalDate day) {
        return day.atTime(time).atZone(ZONE).toInstant();
    }
}
