package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.core.infra.declarative.orchestrators.calendar.CalendarPage;
import io.mateu.ecdemo1.contenthub.application.calendar.CalendarEntry;
import io.mateu.ecdemo1.contenthub.application.calendar.EditorialCalendar;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonSize;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.CalendarEvent;
import io.mateu.uidl.data.CalendarView;
import io.mateu.uidl.data.HorizontalLayout;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * «Calendario editorial»: Mateu's calendar archetype (month, week and list views) with what the
 * {@link EditorialCalendar} gathers — what publishes, expires, must be confirmed by, waits for review —
 * and the filters by type, section and status as rows of buttons above it (the archetype draws its own
 * fields first, so filters as form fields would end up under the calendar). Clicking an entry opens it.
 */
@Service
@Scope("prototype")
@Title("Calendario editorial")
@Editors
public class EditorialCalendarPage extends CalendarPage {

    static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    @Hidden
    public String _type;
    @Hidden
    public String _section;
    @Hidden
    public String _status;

    final EditorialCalendar calendar;
    final Readers readers;

    public EditorialCalendarPage(EditorialCalendar calendar, Readers readers) {
        this.calendar = calendar;
        this.readers = readers;
        var calendarOnly = _calendar;
        _calendar = () -> VerticalLayout.builder()
                .content(List.of(filters(), calendarOnly.call()))
                .fullWidth(true)
                .spacing(true)
                .build();
    }

    @Override
    protected List<CalendarView> views() {
        return List.of(CalendarView.month, CalendarView.week, CalendarView.list);
    }

    List<CalendarEntry> entries(LocalDate month, HttpRequest httpRequest) {
        return calendar.entries(readers.of(httpRequest), month.withDayOfMonth(1), month.withDayOfMonth(month.lengthOfMonth()),
                new EditorialCalendar.Filter(blankToNull(_type), blankToNull(_section), blankToNull(_status)));
    }

    @Override
    protected List<CalendarEvent> events(LocalDate month, HttpRequest httpRequest) {
        return entries(month, httpRequest).stream()
                .map(e -> CalendarEvent.builder()
                        .id(e.id())
                        .title(e.title())
                        .date(e.date())
                        .startTime(e.time() == null ? null : HOUR.format(e.time()))
                        .color(e.color())
                        .build())
                .toList();
    }

    @Override
    protected Object actionOn(CalendarEvent event, HttpRequest httpRequest) {
        return entries(event.date(), httpRequest).stream()
                .filter(e -> Objects.equals(e.id(), event.id()))
                .findFirst()
                .map(e -> (Object) UICommand.navigateTo(e.target()))
                .orElse(this);
    }

    Component filters() {
        var types = new ArrayList<Option>();
        types.add(new Option(null, "Todos los tipos"));
        Arrays.stream(ItemType.values()).forEach(t -> types.add(new Option(t.name(), t.label())));
        var sections = new ArrayList<Option>();
        sections.add(new Option(null, "Todas las secciones"));
        var catalog = Catalog.get();
        if (catalog != null) {
            catalog.sections().forEach(s -> sections.add(new Option(s.value(), s.label().trim())));
        }
        var statuses = new ArrayList<Option>();
        statuses.add(new Option(null, "Todos los estados (sin retirados)"));
        Arrays.stream(ItemStatus.values()).forEach(s -> statuses.add(new Option(s.name(), s.label())));
        return VerticalLayout.builder()
                .content(List.of(row("_type", _type, types), row("_section", _section, sections),
                        row("_status", _status, statuses)))
                .spacing(false)
                .build();
    }

    static Component row(String facet, String current, List<Option> options) {
        var buttons = options.stream()
                .map(o -> (Component) Button.builder()
                        .label(o.label())
                        .actionId("filtrarCalendario")
                        .parameters(Map.of("_facet", facet, "_value", o.value() == null ? "" : String.valueOf(o.value())))
                        .buttonStyle(Objects.equals(blankToNull(current), o.value() == null ? null : String.valueOf(o.value()))
                                ? ButtonStyle.primary : null)
                        .size(ButtonSize.small)
                        .build())
                .toList();
        return HorizontalLayout.builder().spacing(true).style("flex-wrap: wrap; align-items: center;").content(buttons).build();
    }

    /** A filter button: sets one facet (empty: any) and draws the calendar again. */
    @io.mateu.uidl.annotations.Action
    public Object filtrarCalendario(HttpRequest httpRequest) {
        bindRequest(httpRequest);
        var parameters = httpRequest.runActionRq().parameters();
        var facet = parameters.get("_facet");
        var value = parameters.get("_value") == null ? null : blankToNull(String.valueOf(parameters.get("_value")));
        if ("_type".equals(facet)) {
            _type = value;
        } else if ("_section".equals(facet)) {
            _section = value;
        } else if ("_status".equals(facet)) {
            _status = value;
        }
        return this;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
