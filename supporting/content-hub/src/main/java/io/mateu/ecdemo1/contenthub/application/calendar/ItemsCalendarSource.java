package io.mateu.ecdemo1.contenthub.application.calendar;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The items' dates on the calendar: when each publishes (its publish-from), expires and must be confirmed
 * by, and the items waiting for review (on the day they were sent).
 *
 * <p>A published item shows the dates of what readers see (its last version). A draft that was never
 * published shows the dates it plans, marked «(previsto)»; a published item whose draft changes its dates
 * shows those too, marked «(cambios sin publicar)». A draft without a publish-from publishes when it is
 * published: it has no date to show.
 */
@Component
@RequiredArgsConstructor
public class ItemsCalendarSource implements CalendarSource {

    public static final String PUBLISH = "PUBLISH";
    public static final String EXPIRE = "EXPIRE";
    public static final String CONFIRMATION_DEADLINE = "CONFIRMATION_DEADLINE";
    public static final String REVIEW = "REVIEW";

    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    final ItemRepository items;

    @Override
    public List<CalendarEntry> entries(LocalDate from, LocalDate to) {
        var out = new ArrayList<CalendarEntry>();
        for (var item : items.findAll()) {
            out.addAll(of(item));
        }
        return out.stream().filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to)).toList();
    }

    /** Every date of one item, whatever the period. */
    static List<CalendarEntry> of(Item item) {
        var out = new ArrayList<CalendarEntry>();
        var shown = item.status() == ItemStatus.PUBLISHED || item.status() == ItemStatus.RETIRED
                ? item.latestVersion().map(v -> v.content()).orElse(null) : null;
        if (shown != null) {
            dates(out, item, shown, null, "p", "");
        }
        var draft = item.draft();
        if (!item.hasVersions()) {
            dates(out, item, draft, null, "d", item.awaitingPublication() ? " (previsto, en revisión)" : " (previsto)");
        } else if (item.draftChanged()) {
            dates(out, item, draft, shown, "d", " (cambios sin publicar)");
        }
        if (item.awaitingPublication()) {
            out.add(entry(item, draft, REVIEW, "En revisión desde", item.updatedAt(), "r", ""));
        }
        return out;
    }

    /** The dates of {@code content}; with {@code skipSameAs}, only those that differ from it. */
    static void dates(List<CalendarEntry> out, Item item, ItemContent content, ItemContent skipSameAs, String tag,
                      String suffix) {
        if (content.publishAt() != null && (skipSameAs == null || !Objects.equals(content.publishAt(), skipSameAs.publishAt()))) {
            out.add(entry(item, content, PUBLISH, "Se publica", content.publishAt(), tag, suffix));
        }
        if (content.expireAt() != null && (skipSameAs == null || !Objects.equals(content.expireAt(), skipSameAs.expireAt()))) {
            out.add(entry(item, content, EXPIRE, "Caduca", content.expireAt(), tag, suffix));
        }
        if (content.confirmationRequired() && content.confirmationDeadline() != null
                && (skipSameAs == null || !skipSameAs.confirmationRequired()
                || !Objects.equals(content.confirmationDeadline(), skipSameAs.confirmationDeadline()))) {
            out.add(entry(item, content, CONFIRMATION_DEADLINE, "Confirmar antes de", content.confirmationDeadline(), tag, suffix));
        }
    }

    static CalendarEntry entry(Item item, ItemContent content, String kind, String kindLabel, Instant at, String tag,
                               String suffix) {
        var local = LocalDateTime.ofInstant(at, ZONE);
        var title = content.in(item.defaultLanguage(), item.defaultLanguage()).title();
        return new CalendarEntry(kind + ":" + item.id() + ":" + tag, kind, kindLabel, local.toLocalDate(),
                local.toLocalTime().truncatedTo(ChronoUnit.MINUTES), short_(kind) + " · " + (title.isBlank() ? item.id() : title) + suffix,
                "/editorial/contenidos/" + item.id(), color(kind), item.type().name(), content.sectionId(), item.status().name());
    }

    static String short_(String kind) {
        return switch (kind) {
            case PUBLISH -> "Publica";
            case EXPIRE -> "Caduca";
            case CONFIRMATION_DEADLINE -> "Confirmar";
            default -> "Revisión";
        };
    }

    static String color(String kind) {
        return switch (kind) {
            case PUBLISH -> "#2e7d32";
            case EXPIRE -> "#c74634";
            case CONFIRMATION_DEADLINE -> "#b8860b";
            default -> "#3b82f6";
        };
    }
}
