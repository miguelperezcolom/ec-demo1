package io.mateu.ecdemo1.contenthub.application.calendar;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One thing on the editorial calendar, on one day.
 *
 * @param id        unique on the calendar (the source's prefix, the kind, the thing's id…)
 * @param kind      what happens: {@link ItemsCalendarSource#PUBLISH}, … or a source's own
 * @param kindLabel the kind in words: «Se publica», «Caduca»…
 * @param time      when in the day, if it matters (shown in the week and list views); null for all-day
 * @param title     what is shown on the calendar
 * @param target    the backoffice route that opens it
 * @param color     the chip's color (#rrggbb)
 * @param type      the facet the «Tipo» filter looks at (an ItemType name, or the source's own)
 * @param sectionId the facet the «Sección» filter looks at; null when it has none
 * @param status    the facet the «Estado» filter looks at (an ItemStatus name, or the source's own)
 */
public record CalendarEntry(String id, String kind, String kindLabel, LocalDate date, LocalTime time, String title,
                            String target, String color, String type, String sectionId, String status) {
}
