package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.ecdemo1.mdm.footprint.Footprint;
import io.mateu.ecdemo1.mdm.footprint.Links;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.Xref;
import io.mateu.ecdemo1.mdm.store.XrefRepository;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.Identifiable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A customer, for the business: who it is now — the data Salesforce, the master, decided, as the MDM
 * keeps it — where it is known, its reservations in every system and the changes hotels asked for.
 * Read-only: a change is asked for at a hotel's reception and decided in Salesforce.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class CustomerCard implements Identifiable {

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

    @Section("Solicitudes de cambio")
    @ReadOnly
    @Label("")
    @Colspan(2)
    List<ChangeRequestRow> changeRequests;

    final Footprint footprint;
    final Links links;
    final XrefRepository xrefs;
    final ChangeRequestRepository changeRequestRepository;
    final CustomerRepository customers;

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
        changeRequests = changeRequestRepository.findByCustomerIdInOrderByRequestedAtDesc(codes).stream()
                .map(r -> ChangeRequestRows.of(r, name))
                .toList();
        return this;
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
        var customer = id == null ? null : customers.findById(id).orElse(null);
        if (customer == null) {
            return "";
        }
        var html = new StringBuilder();
        var codes = footprint.codesOf(customer);
        var known = new ArrayList<Xref>();
        codes.forEach(code -> known.addAll(xrefs.findByCustomerIdOrderBySystemAscReferenceAsc(code)));

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
                    stay == null ? "" : Html.link(stayLabel(stay), links.frontOfficeStay(stay.id()))));
        }
        // Stays the front office has and no CRS booking of the customer explains: shown all the same.
        for (var stay : stays.values()) {
            rows.add(List.of(Html.escape("—"), Html.escape("Front office"), Html.escape(Estados.day(stay.checkIn())),
                    Html.escape(Estados.day(stay.checkOut())), Html.escape(stay.role()), "", "",
                    Html.link(stayLabel(stay), links.frontOfficeStay(stay.id()))));
        }
        if (rows.isEmpty()) {
            html.append(Html.muted(footprint.readsBookings() ? "No tiene reservas." : "No se leen las reservas del CRS desde aquí."));
        } else {
            html.append(Html.table(List.of("Reserva (CRS)", "Hotel", "Llegada", "Salida", "Papel", "Estado", "Opera",
                    "Front office"), rows));
        }

        var cases = changeRequestRepository.findByCustomerIdInOrderByRequestedAtDesc(codes).stream()
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
