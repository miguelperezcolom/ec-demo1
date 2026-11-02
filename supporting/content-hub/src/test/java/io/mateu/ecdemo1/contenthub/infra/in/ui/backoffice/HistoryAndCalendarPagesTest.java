package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.calendar.EditorialCalendar;
import io.mateu.ecdemo1.contenthub.application.calendar.ItemsCalendarSource;
import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.history.ItemHistory;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemHistoryRepository;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.HistoryEntry;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.navigation.Navigation;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.HorizontalLayout;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.interfaces.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two screens as Mateu calls them: the history's selects are editable fields of its detail content
 * and its actions work on an item with two versions; the calendar finds its reader even when Mateu
 * evaluates it without a request.
 */
class HistoryAndCalendarPagesTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE,
            EnumSet.of(EditorialPermission.EDIT, EditorialPermission.PUBLISH), null);
    static final Reader NOBODY = new Reader("anon", "", EmployeeAttributes.NONE, EnumSet.noneOf(EditorialPermission.class), null);

    final Map<String, Item> stored = new HashMap<>();
    final List<HistoryEntry> rows = new ArrayList<>();
    ItemRepository items;
    Readers readers;
    ItemCommands commands;
    ItemHistory history;
    HttpRequest request;

    @BeforeEach
    void setUp() {
        items = mock(ItemRepository.class);
        when(items.find(any())).thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
        when(items.findAll()).thenAnswer(i -> List.copyOf(stored.values()));
        doAnswer(i -> stored.put(i.<Item>getArgument(0).id(), i.getArgument(0))).when(items).save(any());
        var repo = new ItemHistoryRepository() {
            @Override
            public HistoryEntry append(HistoryEntry entry) {
                var numbered = entry.withSeq((int) rows.stream().filter(r -> r.itemId().equals(entry.itemId())).count() + 1);
                rows.add(numbered);
                return numbered;
            }

            @Override
            public List<HistoryEntry> of(String itemId) {
                return rows.stream().filter(r -> r.itemId().equals(itemId)).toList();
            }
        };
        var directory = mock(EmployeeDirectory.class);
        when(directory.find(any())).thenReturn(Optional.empty());
        var navigation = mock(NavigationRepository.class);
        when(navigation.get()).thenReturn(Navigation.empty());
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        history = new ItemHistory(repo, items, directory, mock(NamedAudienceRepository.class), navigation, clock);
        commands = new ItemCommands(items, mock(DomainEvents.class), directory, mock(NamedAudiences.class), clock);
        commands.setHistory(history);
        request = mock(HttpRequest.class);
        readers = mock(Readers.class);
        when(readers.of(any(HttpRequest.class))).thenAnswer(i -> i.getArgument(0) == request ? EDITOR : NOBODY);
    }

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    static ItemContent content(String title, String body) {
        return new ItemContent("noticias", Audience.EVERYONE, Map.of("es", new Translation(title, "", body)), List.of(),
                Instant.parse("2026-11-20T09:00:00Z"), null, false, false, null, null);
    }

    /** Published twice, then edited again. */
    String itemWithTwoVersions() {
        var id = commands.create(EDITOR, ItemType.ARTICLE, "es", content("Turnos", "<p>Lunes</p>"));
        commands.publish(EDITOR, id, false);
        commands.edit(EDITOR, id, content("Turnos de verano", "<p>Martes</p>"));
        commands.publish(EDITOR, id, false);
        commands.edit(EDITOR, id, content("Turnos de invierno", "<p>Miércoles</p>"));
        return id;
    }

    static List<FormField> fields(Object component) {
        var out = new ArrayList<FormField>();
        collect(component, out, FormField.class);
        return out;
    }

    static String html(Object component) {
        var out = new ArrayList<Element>();
        collect(component, out, Element.class);
        return out.stream().map(Element::content).reduce("", String::concat);
    }

    @SuppressWarnings("unchecked")
    static <T> void collect(Object c, List<T> out, Class<T> type) {
        if (type.isInstance(c)) {
            out.add((T) c);
        } else if (c instanceof VerticalLayout v) {
            v.content().forEach(x -> collect(x, out, type));
        } else if (c instanceof HorizontalLayout h) {
            h.content().forEach(x -> collect(x, out, type));
        }
    }

    @Test
    void the_history_offers_editable_selects_and_compares_and_restores_an_item_with_two_versions() {
        var id = itemWithTwoVersions();
        var page = new HistoryPage(history, items, readers).load(id, request);

        var content = page.detailContent(request);
        var fields = fields(content);
        assertThat(fields).extracting(FormField::id).containsExactly("desde", "hasta", "aRestaurar");
        assertThat(fields).noneMatch(FormField::readOnly);
        assertThat(fields.get(0).options()).extracting(Option::value).containsExactly("draft", "h5", "v2", "h3", "v1", "h1");
        assertThat(fields.get(2).options()).extracting(Option::value).doesNotContain("draft");
        // opened on: the last version against the draft
        assertThat(page.desde).isEqualTo("v2");
        assertThat(html(content)).contains("Diferencias: Versión 2").contains("verano").contains("invierno")
                .contains("<del").contains("<ins").contains("Historial");

        // the user picks two versions and «Comparar»
        page.desde = "v1";
        page.hasta = "v2";
        assertThat(page.comparar(request)).isSameAs(page);
        assertThat(html(page.detailContent(request))).contains("Diferencias: Versión 1").contains("Lunes").contains("Martes")
                .doesNotContain("invierno");

        // and restores version 1
        page.aRestaurar = "v1";
        var result = (List<?>) page.restaurarEstaVersion(request);
        assertThat(result.getFirst()).isSameAs(page);
        var item = stored.get(id);
        assertThat(item.draft().translations().get("es").title()).isEqualTo("Turnos");
        assertThat(item.versions()).hasSize(2);
        assertThat(item.publishedVersion().orElseThrow().content().translations().get("es").title()).isEqualTo("Turnos de verano");
        assertThat(page.desde).isEqualTo("v1");
        assertThat(page.hasta).isEqualTo("draft");
        assertThat(html(page.detailContent(request))).contains("Restaurado");
    }

    @Test
    void the_calendar_finds_its_reader_without_the_request_mateu_passes() {
        itemWithTwoVersions();
        var calendar = new EditorialCalendar(List.of(new ItemsCalendarSource(items)), mock(NavigationRepository.class));
        var page = new EditorialCalendarPage(calendar, readers);
        var november = LocalDate.of(2026, 11, 1);

        // the request Mateu bound to the page
        page.bindRequest(request);
        assertThat(page.events(november, null)).isNotEmpty();

        // none bound: the request being served on this thread
        var fresh = new EditorialCalendarPage(calendar, readers);
        var servlet = new MockHttpServletRequest();
        servlet.addHeader(Readers.SCOPES_HEADER, "content:edit");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servlet));
        when(readers.of(any(Function.class))).thenAnswer(i -> "content:edit".equals(
                i.<Function<String, String>>getArgument(0).apply(Readers.SCOPES_HEADER)) ? EDITOR : NOBODY);
        assertThat(fresh.events(november, null)).extracting(e -> e.title()).contains("Publica · Turnos de verano");
    }
}
