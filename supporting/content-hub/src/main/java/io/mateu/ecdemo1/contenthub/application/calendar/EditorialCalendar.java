package io.mateu.ecdemo1.contenthub.application.calendar;

import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The editorial calendar: every {@link CalendarSource}'s entries for a period, filtered and in order.
 * For any editorial permission.
 */
@Service
@RequiredArgsConstructor
public class EditorialCalendar {

    final List<CalendarSource> sources;
    final NavigationRepository navigation;

    /**
     * What the filters ask: each one null for any. A section admits its subsections too. Without a status,
     * retired items are left out — they no longer publish nor expire.
     */
    public record Filter(String type, String sectionId, String status) {

        public static final Filter NONE = new Filter(null, null, null);
    }

    public List<CalendarEntry> entries(Reader reader, LocalDate from, LocalDate to, Filter filter) {
        if (reader.permissions().isEmpty()) {
            throw new SecurityException("Hace falta un permiso editorial");
        }
        var f = filter == null ? Filter.NONE : filter;
        var nav = f.sectionId() == null ? null : navigation.get();
        var unique = new LinkedHashMap<String, CalendarEntry>();
        for (var source : sources) {
            for (var e : source.entries(from, to)) {
                if (e.date() == null || e.date().isBefore(from) || e.date().isAfter(to)) {
                    continue;
                }
                if (f.type() != null && !f.type().equals(e.type())) {
                    continue;
                }
                if (f.status() != null ? !f.status().equals(e.status()) : ItemStatus.RETIRED.name().equals(e.status())) {
                    continue;
                }
                if (f.sectionId() != null && (e.sectionId() == null
                        || !(e.sectionId().equals(f.sectionId()) || nav.ancestors(e.sectionId()).contains(f.sectionId())))) {
                    continue;
                }
                unique.putIfAbsent(e.id(), e);
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::date)
                        .thenComparing(e -> e.time() == null ? LocalTime.MIN : e.time())
                        .thenComparing(CalendarEntry::title))
                .toList();
    }
}
