package io.mateu.ecdemo1.journey.ui;

import io.mateu.ecdemo1.journey.JourneyProperties;
import io.mateu.ecdemo1.journey.application.BookingJourney;
import io.mateu.ecdemo1.journey.application.Journeys;
import io.mateu.ecdemo1.journey.business.BusinessData;
import io.mateu.ecdemo1.journey.model.Durations;
import io.mateu.ecdemo1.journey.model.Hop;
import io.mateu.ecdemo1.journey.model.Journey;
import io.mateu.ecdemo1.journey.model.JourneyMapper;
import io.mateu.ecdemo1.journey.model.Lane;
import io.mateu.ecdemo1.journey.model.Outcome;
import io.mateu.ecdemo1.journey.model.Tone;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.HorizontalLayout;
import io.mateu.uidl.data.MetricCard;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.Timeline;
import io.mateu.uidl.data.TimelineItem;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.OnLoadTrigger;
import io.mateu.uidl.fluent.Trigger;
import io.mateu.uidl.fluent.TriggersSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A booking's journey: one of its changes — the newest, unless another is asked for — followed
 * across the chain, for a person who does not read traces. What the business asks first (how long
 * until Opera had it, until the front office did) as figures; the systems as lanes on a time axis;
 * every hop, in words, as a timeline; the booking's other changes, the causes it waited on and the
 * way to the technical trace below.
 *
 * <p>Read-only. The screen's state holds only which booking and which change: everything else is
 * read again from {@link Journeys}, which keeps it for a few seconds.
 */
@Service
@Scope("prototype")
public class JourneyView implements Identifiable, TriggersSupplier {

    /** The badge in the header: where the change shown stands. */
    @ReadOnly
    Status status;

    /** The booking's locator, or "locator~traceId" for one of its changes. */
    @Hidden
    String id;

    /** Whether the change shown may still be arriving: the page asks again by itself while it is. */
    @Hidden
    boolean settling;

    @Section("La reserva")
    @ReadOnly
    @Label("Reserva")
    String locator;
    @ReadOnly
    @Label("Hotel")
    String hotel;
    @ReadOnly
    @Label("Cambio mostrado")
    String change;
    @ReadOnly
    @Label("Cómo acabó")
    String outcome;
    @ReadOnly
    @Label("Cliente")
    String customer;
    @ReadOnly
    @Label("En Opera")
    String opera;
    @ReadOnly
    @Label("En el front office")
    String frontOffice;
    @ReadOnly
    @Label("En Salesforce")
    String salesforce;

    @Section("Tiempos")
    @Label("")
    @Colspan(2)
    Callable<Component> times = this::times;

    @Section("Recorrido por sistemas")
    @Label("")
    @Colspan(2)
    Callable<Component> lanes = this::lanes;

    @Section("Paso a paso")
    @Label("")
    @Colspan(2)
    Callable<Component> steps = this::steps;

    final Journeys journeys;
    final JourneyProperties properties;

    public JourneyView(Journeys journeys, JourneyProperties properties) {
        this.journeys = journeys;
        this.properties = properties;
    }

    public JourneyView load(String id) {
        this.id = id;
        var booking = booking();
        var shown = shown(booking);
        locator = booking.locator();
        hotel = booking.hotel() == null ? "" : booking.hotel();
        var business = booking.business();
        customer = customer(business);
        opera = opera(shown, business);
        frontOffice = business.stay() == null ? (shown != null && shown.crsToFrontOffice() != null ? "Estancia grabada" : "Sin estancia todavía")
                : business.stay().statusText();
        salesforce = salesforce(business);
        if (shown == null) {
            change = "";
            outcome = booking.problem() != null ? booking.problem()
                    : "Todavía no hay recorrido: las trazas tardan unos segundos en llegar";
            status = new Status(booking.problem() != null ? StatusType.DANGER : StatusType.NONE,
                    booking.problem() != null ? "Sin Tempo" : "Sin trazas aún");
            settling = booking.problem() == null;
        } else {
            var when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(zone());
            change = "Cambio " + booking.ordinal(shown) + " de " + booking.changes().size() + " · " + shown.kind().label()
                    + (shown.version() == null ? "" : " · versión " + shown.version()) + " · "
                    + when.format(JourneyMarkup.instant(shown.startNanos()));
            outcome = shown.outcomeDetail();
            status = new Status(switch (shown.outcome()) {
                case DONE -> StatusType.SUCCESS;
                case WAITING -> StatusType.WARNING;
                case FAILED -> StatusType.DANGER;
                case IN_PROGRESS -> StatusType.INFO;
                case NOT_PROJECTED -> StatusType.NONE;
            }, shown.outcome().label());
            settling = shown.outcome() == Outcome.IN_PROGRESS || booking.unreadTraces() > 0;
        }
        return this;
    }

    // ── what the screen draws ────────────────────────────────────────────────────────────────────

    Component times() {
        var booking = booking();
        var shown = shown(booking);
        if (shown == null) {
            return new Text(booking.problem() != null ? booking.problem()
                    : "Todavía no hay recorrido para " + booking.locator() + ". Las trazas llegan a Tempo unos segundos "
                    + "después del cambio: esta página vuelve a mirar sola, o pulsa «Actualizar».");
        }
        var start = shown.kind().label().toLowerCase();
        var cards = new ArrayList<Component>();
        cards.add(metric("hasta-opera", "Hasta Opera", shown.crsToOpera(),
                shown.crsToOpera() == null ? "No llegó a Opera en este cambio" : "Desde el cambio en el CRS hasta que Opera la tuvo"));
        cards.add(metric("hasta-fo", "Hasta el front office", shown.crsToFrontOffice(),
                shown.crsToFrontOffice() == null ? "La recepción no la recibió en este cambio" : "Hasta que la recepción la vio"));
        cards.add(metric("total", "Todo el recorrido", shown.total(), "De principio a fin, " + start));
        cards.add(MetricCard.builder().id("sistemas").title("Sistemas").value(String.valueOf(shown.lanes().size()
                        + (booking.business().passengers().stream().anyMatch(p -> p.salesforceContactId() != null) ? 1 : 0)))
                .description(String.join(" → ", shown.lanes().stream().map(Lane::label).toList())).build());
        return HorizontalLayout.builder().id("tiempos").content(cards).spacing(true).wrap(true).fullWidth(true).build();
    }

    static MetricCard metric(String id, String title, Duration duration, String description) {
        return MetricCard.builder().id(id).title(title).value(duration == null ? "—" : Durations.words(duration))
                .description(description).build();
    }

    Component lanes() {
        var booking = booking();
        var shown = shown(booking);
        if (shown == null) {
            return Html.block(booking.unreadTraces() > 0
                    ? Html.muted("Tempo ya conoce " + booking.unreadTraces() + " traza(s) de esta reserva, pero aún las está guardando.")
                    : Html.muted("Nada que dibujar todavía."));
        }
        var story = new Journey(shown.traceId(), shown.locator(), shown.hotel(), shown.kind(), shown.version(),
                shown.startNanos(), shown.endNanos(), shown.crsToOpera(), shown.crsToFrontOffice(), shown.outcome(),
                shown.outcomeDetail(), shown.operaHotel(), shown.operaReservationId(), shown.operaAction(), shown.processes(),
                story(shown, booking.business()), shown.translations(), shown.causes(), shown.identities());
        return Html.block(JourneyMarkup.of(booking, story, properties.grafanaUrl(), zone()));
    }

    Component steps() {
        var booking = booking();
        var shown = shown(booking);
        if (shown == null) {
            return new Text("Los pasos aparecerán aquí en cuanto llegue la traza.");
        }
        var time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(zone());
        var items = new ArrayList<TimelineItem>();
        var n = 0;
        for (var hop : story(shown, booking.business())) {
            var timestamp = hop.timed()
                    ? time.format(JourneyMarkup.instant(hop.startNanos())) + " · +"
                    + Durations.words(Duration.ofNanos(hop.startNanos() - shown.startNanos()))
                    + (hop.durationMillis() > 0 ? " · dura " + Durations.words(Duration.ofMillis(hop.durationMillis())) : "")
                    : "fuera de esta traza";
            items.add(TimelineItem.builder()
                    .id("paso-" + n++)
                    .title(hop.lane().label() + " · " + hop.title())
                    .description(hop.detail())
                    .timestamp(timestamp)
                    .icon(switch (hop.tone()) {
                        case OK -> "✓";
                        case WAIT -> "…";
                        case ERROR -> "!";
                        case INFO -> "i";
                    })
                    .color(hop.tone() == Tone.ERROR ? "#dc2626" : hop.lane().color())
                    .build());
        }
        return Timeline.builder().id("pasos").items(items).build();
    }

    /**
     * The change's hops, with what the systems say now where the trace does not: the people the
     * MDM resolved, the stay's state in the front office, the equivalences in force, and the
     * contacts in Salesforce — which the MDM sends on its own, outside the booking's trace.
     */
    static List<Hop> story(Journey change, BusinessData business) {
        var hops = new ArrayList<Hop>();
        for (var hop : change.hops()) {
            if (hop.lane() == Lane.MDM && change.identities().isEmpty() && !business.passengers().isEmpty()) {
                hop = hop.withDetail(String.join(" · ", business.passengers().stream()
                        .map(p -> p.who() + ": " + p.name() + " (" + p.customerId() + ")").toList()));
            }
            if (hop.lane() == Lane.FRONT_OFFICE && business.stay() != null) {
                hop = new Hop(hop.lane(), hop.startNanos(), hop.endNanos(), hop.title(),
                        hop.detail() + " · hoy: " + business.stay().statusText(), hop.tone(), business.stay().url());
            }
            if (hop.lane() == Lane.MAPPING && hop.title().startsWith("Preparar") && change.translations().isEmpty()
                    && change.causes().isEmpty() && !business.translations().isEmpty()) {
                hop = hop.withDetail("Equivalencias vigentes: " + String.join(" · ",
                        business.translations().stream().map(JourneyMapper::translation).toList()));
            }
            hops.add(hop);
        }
        for (var passenger : business.passengers()) {
            if (passenger.salesforceContactId() != null) {
                hops.add(new Hop(Lane.SALESFORCE, 0, 0, "Contacto en Salesforce",
                        passenger.who() + " " + passenger.name() + " · contacto " + passenger.salesforceContactId()
                                + " · el MDM lo envía por su cuenta, fuera de esta traza",
                        Tone.OK, passenger.salesforceContactUrl()));
            } else if (passenger.customerId() != null) {
                hops.add(new Hop(Lane.SALESFORCE, 0, 0, "Pendiente de Salesforce",
                        passenger.who() + " " + passenger.name() + " · el MDM aún no lo ha enviado", Tone.INFO, null));
            }
        }
        return hops;
    }

    // ── actions ──────────────────────────────────────────────────────────────────────────────────

    /** Asks Tempo again: a change made a moment ago arrives in a few seconds. */
    @Toolbar
    @Action
    @Label("Actualizar")
    public Object actualizar(HttpRequest httpRequest) {
        journeys.forget(locatorOf(id));
        return load(id);
    }

    @Toolbar
    @Action
    @Label("Ver la reserva")
    public Object verReserva(HttpRequest httpRequest) {
        return UICommand.navigateTo("/booking/bookings/" + encode(locatorOf(id)));
    }

    /** While the change may still be arriving, the page asks again every few seconds, a few times. */
    @Override
    public List<Trigger> triggers(HttpRequest httpRequest) {
        return List.of(new OnLoadTrigger("actualizar", 4000, 1, "state.settling", true));
    }

    // ── bits ─────────────────────────────────────────────────────────────────────────────────────

    BookingJourney booking() {
        return journeys.of(locatorOf(id));
    }

    Journey shown(BookingJourney booking) {
        return booking.change(traceOf(id));
    }

    ZoneId zone() {
        return ZoneId.of(properties.zone());
    }

    static String customer(BusinessData business) {
        if (business.passengers().isEmpty()) {
            return business.booking() == null ? "" : business.booking().holder();
        }
        var holder = business.passengers().get(0);
        var more = business.passengers().size() - 1;
        return holder.name() + " · " + holder.customerId() + (more > 0 ? " (y " + more + " más)" : "");
    }

    static String opera(Journey shown, BusinessData business) {
        var id = shown != null && shown.operaReservationId() != null ? shown.operaReservationId()
                : business.booking() == null ? null : business.booking().pmsReservationId();
        if (id == null) {
            return "Todavía no";
        }
        var hotel = shown == null || shown.operaHotel() == null ? "" : " · hotel " + shown.operaHotel();
        var profiles = business.operaProfiles().isEmpty() ? "" : " · perfil " + String.join(", ", business.operaProfiles());
        return "Reserva " + id + hotel + profiles;
    }

    static String salesforce(BusinessData business) {
        var contacts = business.passengers().stream().filter(p -> p.salesforceContactId() != null)
                .map(p -> p.salesforceContactId()).toList();
        if (!contacts.isEmpty()) {
            return "Contacto " + String.join(", ", contacts);
        }
        return business.passengers().isEmpty() ? "" : "Pendiente de enviar";
    }

    /** The route of a booking's journey, or of one of its changes. */
    static String route(String locator, String traceId) {
        // Locators and trace ids are letters and digits: nothing to encode, and "~" must stay "~".
        return "/journey/bookings/" + (traceId == null ? locator : locator + "~" + traceId);
    }

    static String locatorOf(String id) {
        return id == null ? "" : id.contains("~") ? id.substring(0, id.indexOf('~')) : id;
    }

    static String traceOf(String id) {
        return id != null && id.contains("~") ? id.substring(id.indexOf('~') + 1) : null;
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Recorrido de " + (locator == null ? "la reserva" : locator);
    }
}
