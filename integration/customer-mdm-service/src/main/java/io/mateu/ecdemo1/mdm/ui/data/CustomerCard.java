package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.ecdemo1.mdm.footprint.Footprint;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.ecdemo1.mdm.footprint.Links;
import io.mateu.ecdemo1.mdm.application.CustomerQueries;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.Xref;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged.NoticeType;
import io.mateu.ecdemo1.mdm.notice.CustomerNotices;
import io.mateu.ecdemo1.mdm.store.CustomerNotice;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.State;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.StereotypeSupplier;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.Identifiable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * A customer, for the business: who it is now — the data Salesforce, the master, decided, as the MDM
 * keeps it — where it is known, its reservations in every system and the changes hotels asked for.
 * Read-only: a change is asked for at a hotel's reception and decided in Salesforce.
 *
 * <p>Except its reception notices — what the desk must know when the customer arrives or leaves —
 * which are asked for here, added, changed or deactivated: Salesforce is their master, so each one
 * is written there and shown as pending until Salesforce confirms it.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class CustomerCard implements Identifiable, OptionsSupplier, StereotypeSupplier {

    /** A notice's type, as the desk reads it. */
    public enum TipoAviso {
        Informativo(NoticeType.INFORMATIVE), Importante(NoticeType.IMPORTANT), Bloqueante(NoticeType.BLOCKING);

        final NoticeType type;

        TipoAviso(NoticeType type) {
            this.type = type;
        }

        static TipoAviso of(String type) {
            for (var t : values()) {
                if (t.type.name().equals(type)) {
                    return t;
                }
            }
            return Informativo;
        }
    }

    /** The badge in the header. */
    @ReadOnly
    Status status;

    @Section("Datos vigentes")
    @ReadOnly
    @Label("Nombre")
    String firstName;
    @ReadOnly
    @Label("Apellidos")
    String lastName;
    @ReadOnly
    @Label("Email")
    String email;
    @ReadOnly
    @Label("Teléfono")
    String phone;
    @ReadOnly
    @Label("Documento")
    String document;
    @ReadOnly
    @Label("Nacionalidad")
    String nationality;
    @ReadOnly
    @Label("Fecha de nacimiento")
    String birthDate;
    @ReadOnly
    @Label("Código de cliente")
    String id;
    @ReadOnly
    @Label("Códigos fusionados en él")
    String aliases;
    @ReadOnly
    @Label("En Salesforce")
    String salesforce;
    @ReadOnly
    @Label("Actualizado")
    String updatedAt;

    /** Links, drawn as markup: see {@link Html}. */
    @Section("Dónde está y sus reservas")
    @Label("")
    @Colspan(2)
    Callable<Component> systems = () -> Html.block(systemsMarkup());

    /** The customer's notices and where each one stands with Salesforce. */
    @Section("Avisos de recepción")
    @Label("")
    @Colspan(2)
    Callable<Component> avisos = () -> Html.block(noticesMarkup());

    /** Empty: a new notice. A notice's id: the one the actions change. */
    @Section("Nuevo aviso o cambio")
    @Label("Aviso")
    String avisoSeleccionado;
    @Label("Texto")
    @Stereotype(FieldStereotype.textarea)
    @Colspan(2)
    String avisoTexto;
    @Label("Tipo")
    TipoAviso avisoTipo = TipoAviso.Informativo;
    @Label("Activo")
    boolean avisoActivo = true;
    @Label("Desde")
    LocalDate avisoDesde;
    @Label("Hasta")
    LocalDate avisoHasta;
    @Label("Mostrar en el check-in")
    boolean avisoCheckIn = true;
    @Label("Mostrar en el check-out")
    boolean avisoCheckOut;
    @Label("Mostrar durante la estancia")
    boolean avisoEstancia;

    @Section("Solicitudes de cambio")
    @ReadOnly
    @Label("")
    @Colspan(2)
    List<ChangeRequestRow> changeRequests;

    final Footprint footprint;
    final Links links;
    final CustomerQueries customers;
    final CustomerNotices notices;

    public CustomerCard load(Customer c) {
        status = Estados.customer(c.status);
        id = c.id;
        firstName = c.firstName;
        lastName = c.lastName;
        email = c.email;
        phone = c.phone;
        nationality = c.nationality;
        birthDate = Estados.day(c.birthDate);
        document = c.documentNumber == null ? "" : (c.documentType == null ? "" : c.documentType + " ") + c.documentNumber;
        var codes = footprint.codesOf(c);
        aliases = String.join(", ", codes.subList(1, codes.size()));
        salesforce = salesforce(c);
        updatedAt = Estados.moment(c.updatedAt);
        var name = c.fullName();
        changeRequests = customers.changeRequestsOf(codes).stream()
                .map(r -> ChangeRequestRows.of(r, name))
                .toList();
        return this;
    }

    // ── reception notices ──────────────────────────────────────────────────────

    @Toolbar
    @Action
    public Object guardarAviso(HttpRequest httpRequest) {
        var draft = new CustomerNotices.Draft(avisoTexto, avisoTipo == null ? null : avisoTipo.type, avisoDesde,
                avisoHasta, moments(), avisoActivo);
        var by = io.mateu.ecdemo1.uicommons.user.ConsoleUser.of(httpRequest);
        CustomerNotice saved;
        try {
            saved = avisoSeleccionado == null || avisoSeleccionado.isBlank()
                    ? notices.create(id, draft, by) : notices.change(avisoSeleccionado, draft, by);
        } catch (IllegalArgumentException e) {
            return new Message(e.getMessage());
        }
        clearNotice();
        reload();
        return List.of(new Message("Aviso " + saved.id + " enviado a Salesforce: pendiente hasta que lo confirme"),
                new State(this));
    }

    @Toolbar
    @Action
    public Object editarAviso(HttpRequest httpRequest) {
        if (avisoSeleccionado == null || avisoSeleccionado.isBlank()) {
            return new Message("Elige en «Aviso» cuál quieres editar");
        }
        var n = notices.get(avisoSeleccionado);
        var pending = n.pending() && n.pendingText != null;
        avisoTexto = pending ? n.pendingText : n.text;
        avisoTipo = TipoAviso.of(pending ? n.pendingType : n.type);
        avisoDesde = pending ? n.pendingFrom : n.fromDate;
        avisoHasta = pending ? n.pendingTo : n.toDate;
        avisoActivo = pending ? Boolean.TRUE.equals(n.pendingActive) : n.active;
        var showAt = pending ? n.pendingShowAt : n.showAt;
        avisoCheckIn = showAt != null && showAt.contains(NoticeMoment.CHECK_IN.name());
        avisoCheckOut = showAt != null && showAt.contains(NoticeMoment.CHECK_OUT.name());
        avisoEstancia = showAt != null && showAt.contains(NoticeMoment.STAY.name());
        return new State(this);
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "¿Desactivar el aviso?",
            confirmationMessage = "Recepción deja de verlo en cuanto Salesforce lo confirme. Se puede volver a activar editándolo.")
    public Object desactivarAviso(HttpRequest httpRequest) {
        if (avisoSeleccionado == null || avisoSeleccionado.isBlank()) {
            return new Message("Elige en «Aviso» cuál quieres desactivar");
        }
        var n = notices.deactivate(avisoSeleccionado, io.mateu.ecdemo1.uicommons.user.ConsoleUser.of(httpRequest));
        clearNotice();
        reload();
        return List.of(new Message("Aviso " + n.id + " desactivado: pendiente hasta que Salesforce lo confirme"),
                new State(this));
    }

    @Toolbar
    @Action
    public Object nuevoAviso(HttpRequest httpRequest) {
        clearNotice();
        return new State(this);
    }

    Set<NoticeMoment> moments() {
        var set = EnumSet.noneOf(NoticeMoment.class);
        if (avisoCheckIn) {
            set.add(NoticeMoment.CHECK_IN);
        }
        if (avisoCheckOut) {
            set.add(NoticeMoment.CHECK_OUT);
        }
        if (avisoEstancia) {
            set.add(NoticeMoment.STAY);
        }
        return set;
    }

    void clearNotice() {
        avisoSeleccionado = null;
        avisoTexto = null;
        avisoTipo = TipoAviso.Informativo;
        avisoActivo = true;
        avisoDesde = null;
        avisoHasta = null;
        avisoCheckIn = true;
        avisoCheckOut = false;
        avisoEstancia = false;
    }

    void reload() {
        customers.find(id).ifPresent(this::load);
    }

    /** The notices of the customer, under any of its codes. */
    List<CustomerNotice> customerNotices() {
        var customer = id == null ? null : customers.find(id).orElse(null);
        return customer == null ? List.of() : notices.of(footprint.codesOf(customer));
    }

    String noticesMarkup() {
        var list = customerNotices();
        if (list.isEmpty()) {
            return Html.muted("No tiene avisos. Se añaden abajo, o en Salesforce como un caso del contacto con «Tipo de aviso».");
        }
        var rows = new ArrayList<List<String>>();
        for (var n : list) {
            var confirmed = n.version > 0;
            rows.add(List.of(
                    Html.escape(n.id),
                    Html.escape(confirmed ? n.text : n.pendingText),
                    Html.escape(TipoAviso.of(confirmed ? n.type : n.pendingType).name()),
                    Html.escape(momentsLabel(confirmed ? n.showAt : n.pendingShowAt)),
                    Html.escape(validity(confirmed ? n.fromDate : n.pendingFrom, confirmed ? n.toDate : n.pendingTo)),
                    Html.escape(confirmed ? (n.active ? "Activo" : "Inactivo") : "—"),
                    Html.escape(syncLabel(n)),
                    n.salesforceId == null ? "" : Html.link("Caso " + n.salesforceId, links.salesforceRecord("Case", n.salesforceId))));
        }
        return Html.table(List.of("Aviso", "Texto", "Tipo", "Se muestra en", "Vigencia", "Estado", "Salesforce", "Caso"), rows);
    }

    /** Where the notice stands with Salesforce, and what is pending, in words. */
    static String syncLabel(CustomerNotice n) {
        var sync = n.sync == null ? CustomerNotice.Sync.CONFIRMED : CustomerNotice.Sync.valueOf(n.sync);
        var asked = n.pendingText == null ? "" : " — pedido por " + n.requestedBy + ": «" + n.pendingText + "», "
                + TipoAviso.of(n.pendingType).name().toLowerCase() + (Boolean.TRUE.equals(n.pendingActive) ? "" : ", inactivo");
        return switch (sync) {
            case CONFIRMED -> "Confirmado" + (n.origin == null ? "" : " (creado en " + n.origin + ")");
            case PENDING -> "Pendiente de enviar a Salesforce" + asked;
            case SENT -> "Enviado; pendiente de que Salesforce lo confirme" + asked;
            case FAILED -> "Salesforce no lo aceptó: " + n.sendError + asked;
        };
    }

    static String momentsLabel(String moments) {
        if (moments == null || moments.isBlank()) {
            return "";
        }
        var labels = new ArrayList<String>();
        for (var m : moments.split(",")) {
            labels.add(switch (NoticeMoment.valueOf(m)) {
                case CHECK_IN -> "check-in";
                case CHECK_OUT -> "check-out";
                case STAY -> "estancia";
            });
        }
        return String.join(", ", labels);
    }

    static String validity(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return "Siempre";
        }
        return (from == null ? "…" : Estados.day(from)) + " → " + (to == null ? "…" : Estados.day(to));
    }

    /** «Aviso» is a select of the customer's notices, plus «nuevo». */
    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return CustomerCard.class.equals(formType) && "avisoSeleccionado".equals(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        if (!"avisoSeleccionado".equals(fieldName)) {
            return List.of();
        }
        var options = new ArrayList<Option>();
        options.add(new Option("", "Nuevo aviso"));
        for (var n : customerNotices()) {
            var text = n.version > 0 ? n.text : n.pendingText;
            options.add(new Option(n.id, n.id + " — " + (text == null ? "" : text.length() > 50 ? text.substring(0, 49) + "…" : text)));
        }
        return options;
    }

    @Override
    public FieldStereotype stereotype(String memberName, HttpRequest httpRequest) {
        return "avisoSeleccionado".equals(memberName) ? FieldStereotype.select : null;
    }

    /** Where the customer's data stands with Salesforce, in words. */
    static String salesforce(Customer c) {
        var state = c.salesforceState == null ? SalesforceState.PENDING : c.salesforceState;
        return switch (state) {
            case PROJECTED -> "Al día (versión " + c.version + ")";
            case PENDING -> "Pendiente de enviar";
            case FAILED -> "No aceptado: " + (c.projectionError == null ? "" : c.projectionError);
            case REMOVED -> "Contacto borrado en Salesforce";
            case NOT_PROJECTED -> "No se envía (fusionado)";
        };
    }

    /**
     * Where the customer is known — its Salesforce contact, its guest in the front office, its Opera
     * profiles — and its reservations: one row per CRS booking, with the booking in Opera and the stay
     * in the front office beside it.
     */
    String systemsMarkup() {
        // Read again by its code: the screen's state holds what it shows, not the record.
        var customer = id == null ? null : customers.find(id).orElse(null);
        if (customer == null) {
            return "";
        }
        var html = new StringBuilder();
        var codes = footprint.codesOf(customer);
        var known = new ArrayList<Xref>();
        codes.forEach(code -> known.addAll(customers.xrefsOf(code)));

        html.append(Html.heading("Dónde está"));
        var places = new ArrayList<List<String>>();
        var contacts = new HashSet<String>();
        if (customer.salesforceContactId != null) {
            contacts.add(customer.salesforceContactId);
            places.add(List.of("Salesforce", Html.link("Contacto " + customer.salesforceContactId,
                    links.salesforceRecord("Contact", customer.salesforceContactId)), "Maestro de clientes", ""));
        }
        var operaByLocator = new HashMap<String, List<String>>();
        for (var x : known) {
            switch (x.system) {
                case "SALESFORCE" -> {
                    if (contacts.add(x.reference)) {
                        places.add(List.of("Salesforce", Html.link("Contacto " + x.reference,
                                links.salesforceRecord("Contact", x.reference)), "Contacto anterior (fusionado)",
                                Html.escape(Estados.moment(x.seenAt))));
                    }
                }
                case "FRONT_OFFICE" -> places.add(List.of("Front office",
                        Html.escape("Huésped " + x.reference), Html.escape(x.context == null ? "" : "Hotel " + x.context),
                        Html.escape(Estados.moment(x.seenAt))));
                case "OPERA" -> {
                    var locator = x.context == null ? null : x.context.substring(x.context.indexOf('/') + 1);
                    var hotel = x.context == null || !x.context.contains("/") ? "" : x.context.substring(0, x.context.indexOf('/'));
                    places.add(List.of("Opera", Html.escape("Perfil " + x.reference),
                            locator == null ? "" : Html.escape("Hotel " + hotel + " · reserva ") + Html.link(shortId(locator), Links.booking(locator)),
                            Html.escape(Estados.moment(x.seenAt))));
                    if (locator != null) {
                        operaByLocator.computeIfAbsent(locator, k -> new ArrayList<>()).add(x.reference);
                    }
                }
                default -> places.add(List.of(Html.escape(x.system), Html.escape(x.reference), Html.escape(x.context), ""));
            }
        }
        if (places.isEmpty()) {
            html.append(Html.muted("Todavía no está en ningún otro sistema."));
        } else {
            html.append(Html.table(List.of("Sistema", "Referencia", "Dónde", "Visto"), places));
        }

        html.append(Html.heading("Reservas"));
        var reservations = footprint.reservations(customer);
        var stays = new HashMap<String, Footprint.Stay>();
        footprint.stays(customer).forEach(s -> stays.put(s.id(), s));
        var rows = new ArrayList<List<String>>();
        for (var r : reservations) {
            var b = r.booking();
            var opera = new ArrayList<String>();
            if (b != null && b.pmsReservationId() != null) {
                opera.add("Reserva " + b.pmsReservationId());
            }
            operaByLocator.getOrDefault(r.locator(), List.of()).forEach(p -> opera.add("Perfil " + p));
            var stay = stays.remove(r.locator());
            rows.add(List.of(
                    Html.link(shortId(r.locator()), Links.booking(r.locator())),
                    Html.escape(r.hotelCode()),
                    Html.escape(b == null ? "" : Estados.day(b.arrival())),
                    Html.escape(b == null ? "" : Estados.day(b.departure())),
                    Html.escape(r.role()),
                    Html.escape(b == null ? "Sin respuesta del CRS" : Estados.booking(b.status())),
                    Html.escape(String.join(" · ", opera)),
                    stay == null ? "" : Html.link(stayLabel(stay), links.frontOfficeStay(stay.id())),
                    Html.link("Ver recorrido", Links.journey(r.locator()))));
        }
        // Stays the front office has and no CRS booking of the customer explains: shown all the same.
        for (var stay : stays.values()) {
            rows.add(List.of(Html.escape("—"), Html.escape("Front office"), Html.escape(Estados.day(stay.checkIn())),
                    Html.escape(Estados.day(stay.checkOut())), Html.escape(stay.role()), "", "",
                    Html.link(stayLabel(stay), links.frontOfficeStay(stay.id())), ""));
        }
        if (rows.isEmpty()) {
            html.append(Html.muted(footprint.readsBookings() ? "No tiene reservas." : "No se leen las reservas del CRS desde aquí."));
        } else {
            html.append(Html.table(List.of("Reserva (CRS)", "Hotel", "Llegada", "Salida", "Papel", "Estado", "Opera",
                    "Front office", "Recorrido"), rows));
        }

        var cases = customers.changeRequestsOf(codes).stream()
                .filter(r -> r.salesforceCaseId != null).toList();
        if (!cases.isEmpty()) {
            html.append(Html.heading("Solicitudes de cambio en Salesforce"));
            html.append(Html.table(List.of("Solicitud", "Caso", "Estado"), cases.stream().map(r -> List.of(
                    Html.escape(r.id),
                    Html.link("Caso " + r.salesforceCaseId, links.salesforceRecord("Case", r.salesforceCaseId)),
                    Html.escape(Estados.changeRequest(r.status).message()))).toList()));
        }
        return html.toString();
    }

    static String stayLabel(Footprint.Stay stay) {
        var label = new StringBuilder(Estados.stay(stay.status()));
        if (stay.room() != null && !stay.room().isBlank()) {
            label.append(" · hab. ").append(stay.room());
        }
        if (!"Huésped".equals(stay.role())) {
            label.append(" · ").append(stay.role().toLowerCase());
        }
        return label.toString();
    }

    /** A CRS locator is a UUID: its last block is what a person reads, as the grids show it. */
    static String shortId(String id) {
        if (id == null) {
            return "";
        }
        var dash = id.lastIndexOf('-');
        return id.length() > 20 && dash > 0 ? "…" + id.substring(dash) : id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        var name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return name.isEmpty() ? "Cliente" : name;
    }
}
