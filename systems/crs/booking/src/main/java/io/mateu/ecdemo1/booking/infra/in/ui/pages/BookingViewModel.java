package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;
import io.mateu.ecdemo1.booking.application.usecases.booking.confirm.ConfirmBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.confirm.ConfirmBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.update.UpdateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.update.UpdateBookingUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;
import io.mateu.ecdemo1.booking.infra.in.ui.suppliers.CatalogLookup;
import io.mateu.ecdemo1.booking.infra.out.mdm.CustomerLinks;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.DetailFormCustomisation;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.HiddenInEditor;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.FormPosition;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;

/**
 * The booking form. Rooms are numbered in the order they are listed; guests name the line of their
 * room. Prices are not entered — the CRS works them out when the booking is saved.
 *
 * <p>No validation on a field hidden in the creation form: Mateu validates hidden fields too, and
 * one would make the form impossible to submit.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class BookingViewModel implements Identifiable, VisibilitySupplier {

    static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /** Shown as the badge in the header. Not blank in the creation form, or the badge renders its template. */
    @ReadOnly
    @HiddenInCreate
    Status status = new Status(StatusType.NONE, "New");

    @Section("Booking")
    @NotEmpty
    @Lookup(search = CatalogLookup.class, label = CatalogLookup.class)
    String hotelCode;
    @NotEmpty
    @Lookup(search = CatalogLookup.class, label = CatalogLookup.class)
    String channelCode;
    String partnerCode;
    String externalReference;
    @NotNull
    LocalDate arrival;
    @NotNull
    LocalDate departure;

    @Section("Holder")
    @NotEmpty
    String holderFirstName;
    @NotEmpty
    String holderLastName;
    String holderEmail;
    String holderPhone;
    String holderNationality;

    // The lists show a few columns each and open a row in a modal, where all of its fields fit: a
    // row edited beside the list had too many fields for the space left to it.
    @Section("Rooms")
    @DetailFormCustomisation(position = FormPosition.modal)
    @Colspan(2)
    List<RoomViewModel> rooms;

    @Section("Guests")
    @DetailFormCustomisation(position = FormPosition.modal)
    @Colspan(2)
    List<GuestViewModel> guests;

    @Section("Payments")
    @DetailFormCustomisation(position = FormPosition.modal)
    @Colspan(2)
    List<PaymentViewModel> payments;

    @Section("Amounts")
    @ReadOnly
    @HiddenInCreate
    String total;
    @ReadOnly
    @HiddenInCreate
    String paid;

    @Section("Comments")
    @Stereotype(FieldStereotype.textarea)
    @Colspan(2)
    String comments;

    @Section("Cancellation")
    @ReadOnly
    @HiddenInCreate
    String cancellation;

    @Section("Tracking")
    @ReadOnly
    @HiddenInCreate
    String id;
    @ReadOnly
    @HiddenInCreate
    Long version;
    @ReadOnly
    @HiddenInCreate
    String pmsReservationId;
    @ReadOnly
    @HiddenInCreate
    String created;
    @ReadOnly
    @HiddenInCreate
    String updated;

    /**
     * Where the booking and its people are in the chain's other systems — Opera, the front office,
     * Clientes, Salesforce — as links. Only on the booking's page: nothing to link before it exists.
     */
    @Section("In other systems")
    @HiddenInCreate
    @HiddenInEditor
    @Label("")
    @Colspan(2)
    Callable<Component> otherSystems = this::otherSystems;

    /** Who did what with the booking, and when: here, through the console's agent, and at the front office. */
    @Section("History")
    @HiddenInCreate
    @HiddenInEditor
    @Label("")
    @Colspan(2)
    Callable<Component> history = this::history;

    final CreateBookingUseCase createBookingUseCase;
    final UpdateBookingUseCase updateBookingUseCase;
    final ConfirmBookingUseCase confirmBookingUseCase;
    final BookingCancellationForm cancellationForm;
    final RegisterPaymentUseCase registerPaymentUseCase;
    final BookingQueryService queryService;
    final CustomerLinks customerLinks;
    final io.mateu.ecdemo1.booking.infra.out.audit.BookingHistory bookingHistory;

    public String create(HttpRequest httpRequest) {
        return createBookingUseCase.handle(new CreateBookingCommand(hotelCode, request(), null,
                BookingRequests.newPayments(payments)));
    }

    public void save(HttpRequest httpRequest) {
        var stored = queryService.getById(id).orElseThrow(() -> new NoSuchElementException("Booking not found: " + id));
        if (!stored.hotelCode().equals(hotelCode)) {
            throw new IllegalArgumentException("A booking cannot move to another hotel: cancel it and create a new one");
        }
        updateBookingUseCase.handle(new UpdateBookingCommand(id, request()));
        BookingRequests.registerNewPayments(registerPaymentUseCase, id, payments);
    }

    @Toolbar
    @Action
    public Object confirmBooking(HttpRequest httpRequest) {
        confirmBookingUseCase.handle(new ConfirmBookingCommand(requireSaved()));
        return reloaded("Booking confirmed");
    }

    /** Asks why, in a dialog, and cancels. A cancelled booking cannot be modified or confirmed again. */
    @Toolbar
    @Action
    public Object cancelBooking(HttpRequest httpRequest) {
        var saved = requireSaved();
        return cancellationForm.dialogFor(List.of(saved), BookingCrudOrchestrator.LIST_ROUTE + "/" + saved);
    }

    /**
     * The booking's journey across the chain — CRS, integration, engine, mapping, MDM, Opera, front
     * office, Salesforce — hop by hop, from its traces: journey-service's screen, on this console.
     */
    @Toolbar
    @Action
    @Label("Ver recorrido")
    public Object verRecorrido(HttpRequest httpRequest) {
        return UICommand.navigateTo(OtherSystems.journey(requireSaved()));
    }

    /**
     * Each action only where it can do something: confirming is for a pending booking — a confirmed
     * one would be confirmed again to no effect — and a cancelled booking can be neither confirmed
     * nor cancelled again. A new one has none of them, the journey included: it is saved first.
     */
    @Override
    public boolean isHidden(String memberName, HttpRequest httpRequest) {
        return switch (memberName) {
            case "confirmBooking" -> !is(BookingStatus.Pending);
            case "cancelBooking" -> id == null || is(BookingStatus.Cancelled);
            case "verRecorrido" -> id == null;
            default -> false;
        };
    }

    private boolean is(BookingStatus state) {
        return id != null && status != null && state.name().equals(status.message());
    }

    private String requireSaved() {
        if (id == null) {
            throw new IllegalStateException("Save the booking first");
        }
        return id;
    }

    private List<Object> reloaded(String message) {
        load(queryService.getById(id).orElseThrow());
        return List.of(new Message(message), new State(this));
    }

    private BookingRequest request() {
        return BookingRequests.of(channelCode, partnerCode, externalReference, arrival, departure,
                new Holder(holderFirstName, holderLastName, holderEmail, holderPhone, holderNationality),
                rooms, guests, comments);
    }

    Component history() {
        var entries = bookingHistory.of(id);
        if (entries.isEmpty()) {
            return io.mateu.uidl.data.Text.builder().text("The history is not available now.").build();
        }
        if (entries.get().isEmpty()) {
            return io.mateu.uidl.data.Text.builder().text("Nobody has done anything with this booking yet.").build();
        }
        var zone = java.time.ZoneId.of("Europe/Madrid");
        var when = java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm");
        return io.mateu.uidl.data.StatusList.builder().compact(true).frameless(true).style("width: 100%;")
                .items(entries.get().stream().map(e -> io.mateu.uidl.data.StatusItem.builder()
                        .id("hist-" + (e.at() == null ? "" : e.at().toEpochMilli()) + "-" + Math.abs((e.action() + e.by()).hashCode()))
                        .title(e.action() + ("front-office".equals(e.service()) ? " · front office" : ""))
                        .description((e.at() == null ? "" : when.format(e.at().atZone(zone)) + " · ")
                                + (e.by() == null ? "—" : e.by())
                                + (e.response() == null || e.response().isBlank() || "OK".equals(e.response()) ? ""
                                : " — " + e.response()))
                        .status(e.succeeded() ? "Done" : "Not done")
                        .statusColor(e.succeeded() ? "success" : "danger")
                        .build()).toList())
                .build();
    }

    Component otherSystems() {
        return OtherSystems.of(id, pmsReservationId, id == null ? null : customerLinks.of(hotelCode, id).orElse(null));
    }

    @Override
    public String id() {
        return id;
    }

    public BookingViewModel load(BookingDto booking) {
        id = booking.id();
        status = new Status(switch (booking.status()) {
            case Pending -> StatusType.INFO;
            case Confirmed -> StatusType.SUCCESS;
            case Cancelled -> StatusType.DANGER;
        }, booking.status().name());
        version = booking.version();
        pmsReservationId = booking.pmsReference() != null ? booking.pmsReference().reservationId() : null;
        hotelCode = booking.hotelCode();
        channelCode = booking.channelCode();
        partnerCode = booking.partnerCode();
        externalReference = booking.externalReference();
        arrival = booking.arrival();
        departure = booking.departure();
        holderFirstName = booking.holder().firstName();
        holderLastName = booking.holder().lastName();
        holderEmail = booking.holder().email();
        holderPhone = booking.holder().phone();
        holderNationality = booking.holder().nationality();
        rooms = booking.rooms().stream()
                .map(r -> new RoomViewModel(r.line(), r.roomTypeCode(), r.ratePlanCode(), r.boardCode(),
                        r.adults(), r.childrenAges(), r.total()))
                .toList();
        guests = booking.rooms().stream()
                .flatMap(r -> r.guests().stream().map(g -> new GuestViewModel(r.line(), g.firstName(),
                        g.lastName(), g.type(), g.age(), g.birthDate(), g.nationality(), g.documentType(),
                        g.documentNumber())))
                .toList();
        payments = booking.payments().stream()
                .map(p -> new PaymentViewModel(p.paymentId(), p.type(), p.methodCode(), p.amount(), p.date(),
                        p.reference()))
                .toList();
        total = booking.totalAmount().toPlainString() + " " + booking.currency()
                + " (" + booking.nights() + " nights)";
        paid = booking.paidAmount().toPlainString() + " " + booking.currency();
        comments = booking.comments();
        cancellation = booking.cancellation() != null
                ? booking.cancellation().reasonCode() + " at " + TIMESTAMP.format(booking.cancellation().cancelledAt())
                : null;
        created = TIMESTAMP.format(booking.created());
        updated = TIMESTAMP.format(booking.updated());
        return this;
    }

    @Override
    public String toString() {
        return id != null ? id + " · " + holderFirstName + " " + holderLastName : "New booking";
    }
}
