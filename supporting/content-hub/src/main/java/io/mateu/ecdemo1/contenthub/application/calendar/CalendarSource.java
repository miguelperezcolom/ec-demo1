package io.mateu.ecdemo1.contenthub.application.calendar;

import java.time.LocalDate;
import java.util.List;

/**
 * Something that puts entries on the editorial calendar. The items' dates are one source
 * ({@link ItemsCalendarSource}); a newsletter's scheduled issues, a survey's opening and closing, or
 * anything with a date an editor plans around, is another: a Spring bean implementing this, and
 * {@link EditorialCalendar} picks it up — nothing else changes.
 *
 * <p>An entry's {@code type}, {@code sectionId} and {@code status} are what the screen's filters match;
 * a source whose things have no section leaves it null (they then show only while no section is chosen).
 */
public interface CalendarSource {

    /** The entries dated from {@code from} to {@code to}, both included (Europe/Madrid days). */
    List<CalendarEntry> entries(LocalDate from, LocalDate to);
}
