package io.mateu.ecdemo1.contenthub.application.calendar;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.navigation.Navigation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What the editorial calendar gathers from the items (and any other source), and its filters. */
class EditorialCalendarTest {

    static final Instant T0 = Instant.parse("2026-11-02T09:00:00Z");
    static final LocalDate NOV1 = LocalDate.of(2026, 11, 1);
    static final LocalDate NOV30 = LocalDate.of(2026, 11, 30);
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.EDIT), null);

    final List<Item> items = new ArrayList<>();
    final List<CalendarSource> sources = new ArrayList<>();
    EditorialCalendar calendar;

    @BeforeEach
    void setUp() {
        var repo = mock(ItemRepository.class);
        when(repo.findAll()).thenReturn(items);
        sources.add(new ItemsCalendarSource(repo));
        var nav = Navigation.empty();
        nav.add("empresa", null, Map.of("es", "La empresa"));
        nav.add("noticias", "empresa", Map.of("es", "Noticias"));
        nav.add("cocina", null, Map.of("es", "Cocina"));
        var navigation = mock(NavigationRepository.class);
        when(navigation.get()).thenReturn(nav);
        calendar = new EditorialCalendar(sources, navigation);
    }

    static ItemContent content(String title, String section, Instant publishAt, Instant expireAt, Instant deadline) {
        return new ItemContent(section, Audience.EVERYONE, Map.of("es", new Translation(title, "", "")), List.of(),
                publishAt, expireAt, false, deadline != null, deadline, null);
    }

    static Instant at(String iso) {
        return Instant.parse(iso);
    }

    @Test
    void a_published_item_shows_when_it_publishes_expires_and_must_be_confirmed_by() {
        var item = Item.create("a1", ItemType.NOTICE, "eva", "es", content("Simulacro", "noticias",
                at("2026-11-05T08:30:00Z"), at("2026-11-20T23:00:00Z"), at("2026-11-12T10:00:00Z")), T0);
        item.publish("pablo", true, false, T0);
        items.add(item);

        var entries = calendar.entries(EDITOR, NOV1, NOV30, EditorialCalendar.Filter.NONE);

        assertThat(entries).extracting(CalendarEntry::kind, CalendarEntry::date, CalendarEntry::time).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ItemsCalendarSource.PUBLISH, LocalDate.of(2026, 11, 5), LocalTime.of(9, 30)),
                org.assertj.core.groups.Tuple.tuple(ItemsCalendarSource.CONFIRMATION_DEADLINE, LocalDate.of(2026, 11, 12), LocalTime.of(11, 0)),
                // 23:00 UTC is already the next day in Madrid
                org.assertj.core.groups.Tuple.tuple(ItemsCalendarSource.EXPIRE, LocalDate.of(2026, 11, 21), LocalTime.of(0, 0)));
        assertThat(entries.getFirst().title()).isEqualTo("Publica · Simulacro");
        assertThat(entries.getFirst().target()).isEqualTo("/editorial/contenidos/a1");
    }

    @Test
    void drafts_show_their_planned_dates_and_review_waits_on_the_day_it_was_sent() {
        var planned = Item.create("d1", ItemType.ARTICLE, "eva", "es", content("Plan", "cocina",
                at("2026-11-15T09:00:00Z"), null, null), T0);
        planned.submitForReview(at("2026-11-03T12:00:00Z"));
        items.add(planned);
        var noDate = Item.create("d2", ItemType.ARTICLE, "eva", "es", content("Sin fecha", "cocina", null, null, null), T0);
        items.add(noDate);

        var entries = calendar.entries(EDITOR, NOV1, NOV30, EditorialCalendar.Filter.NONE);

        assertThat(entries).extracting(CalendarEntry::kind, CalendarEntry::date, CalendarEntry::title).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ItemsCalendarSource.REVIEW, LocalDate.of(2026, 11, 3), "Revisión · Plan"),
                org.assertj.core.groups.Tuple.tuple(ItemsCalendarSource.PUBLISH, LocalDate.of(2026, 11, 15), "Publica · Plan (previsto, en revisión)"));
    }

    @Test
    void a_published_item_with_pending_changes_shows_only_the_dates_its_draft_changes() {
        var item = Item.create("a1", ItemType.ARTICLE, "eva", "es", content("Menú", "cocina",
                at("2026-11-05T09:00:00Z"), at("2026-11-25T09:00:00Z"), null), T0);
        item.publish("pablo", true, false, T0);
        item.editDraft(content("Menú", "cocina", at("2026-11-05T09:00:00Z"), at("2026-11-28T09:00:00Z"), null), T0);
        items.add(item);

        var entries = calendar.entries(EDITOR, NOV1, NOV30, EditorialCalendar.Filter.NONE);

        assertThat(entries).extracting(CalendarEntry::title).containsExactly(
                "Publica · Menú", "Caduca · Menú", "Caduca · Menú (cambios sin publicar)");
    }

    @Test
    void filters_by_type_section_with_its_subsections_and_status_and_leaves_retired_out_by_default() {
        var news = Item.create("n1", ItemType.ARTICLE, "eva", "es", content("Noticia", "noticias", at("2026-11-05T09:00:00Z"), null, null), T0);
        news.publish("pablo", true, false, T0);
        var kitchen = Item.create("k1", ItemType.NOTICE, "eva", "es", content("Cocina", "cocina", at("2026-11-06T09:00:00Z"), null, null), T0);
        var retired = Item.create("r1", ItemType.NOTICE, "eva", "es", content("Retirado", "cocina", at("2026-11-07T09:00:00Z"), null, null), T0);
        retired.publish("pablo", true, false, T0);
        retired.retire(T0);
        items.addAll(List.of(news, kitchen, retired));

        assertThat(ids(new EditorialCalendar.Filter(null, null, null))).containsExactly("n1", "k1");
        assertThat(ids(new EditorialCalendar.Filter("NOTICE", null, null))).containsExactly("k1");
        assertThat(ids(new EditorialCalendar.Filter(null, "empresa", null))).as("a section and below").containsExactly("n1");
        assertThat(ids(new EditorialCalendar.Filter(null, null, "DRAFT"))).containsExactly("k1");
        assertThat(ids(new EditorialCalendar.Filter(null, null, "RETIRED"))).containsExactly("r1");
    }

    @Test
    void other_sources_add_their_entries_and_only_the_period_is_kept() {
        sources.add((from, to) -> List.of(
                new CalendarEntry("issue:7", "NEWSLETTER_SEND", "Se envía", LocalDate.of(2026, 11, 9), LocalTime.of(9, 0),
                        "Envío · Boletín 45", "/editorial/newsletter/7", "#7c3aed", "NEWSLETTER", null, "SCHEDULED"),
                new CalendarEntry("issue:8", "NEWSLETTER_SEND", "Se envía", LocalDate.of(2026, 12, 7), null,
                        "Envío · Boletín 46", "/editorial/newsletter/8", "#7c3aed", "NEWSLETTER", null, "SCHEDULED")));

        assertThat(calendar.entries(EDITOR, NOV1, NOV30, EditorialCalendar.Filter.NONE)).extracting(CalendarEntry::id)
                .containsExactly("issue:7");
        assertThat(calendar.entries(EDITOR, NOV1, NOV30, new EditorialCalendar.Filter(null, "cocina", null)))
                .as("no section: not under a chosen one").isEmpty();
    }

    @Test
    void only_for_editorial_permissions() {
        var nobody = new Reader("lola", "Lola", EmployeeAttributes.NONE, EnumSet.noneOf(EditorialPermission.class), null);
        assertThatThrownBy(() -> calendar.entries(nobody, NOV1, NOV30, null)).isInstanceOf(SecurityException.class);
    }

    List<String> ids(EditorialCalendar.Filter filter) {
        return calendar.entries(EDITOR, NOV1, NOV30, filter).stream().map(e -> e.id().split(":")[1]).distinct().toList();
    }
}
