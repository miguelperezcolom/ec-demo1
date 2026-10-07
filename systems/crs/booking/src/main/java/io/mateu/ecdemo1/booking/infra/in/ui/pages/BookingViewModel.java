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
import io.mateu.uidl.annotations.FoldoutDetail;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.HiddenInEditor;
import io.mateu.uidl.annotations.HiddenInView;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.PageWidth;
import io.mateu.uidl.annotations.PageWidthStyle;
import io.mateu.uidl.annotations.PanelWidth;
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
// The booking's page is an overview — where, when, how much and where it is in Opera — with the rest
// in foldout panels beside it: eleven stacked cards were a long scroll to what matters, and half of
// them empty. What has no value is left out of the page; the editor keeps every field. The amounts
// are badges in the page header, not a panel; the comments go with the rooms and guests they are about,
// and the tracking with the payments: two short panels fewer.
@FoldoutDetail(overview = {"Booking"})
@PageWidth(PageWidthStyle.EDGE_TO_EDGE)
public class BookingViewModel implements Identifiable, VisibilitySupplier, io.mateu.uidl.interfaces.SubtitleSupplier {

    static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /** Shown as the badge in the header. Not blank in the creation form, or the badge renders its template. */
    @ReadOnly
    @HiddenInCreate
    Status status = new Status(StatusType.NONE, "New");

    /**
     * What is still to pay, as a header badge next to the status: amber while something is, green
     * once it is paid. Null in the creation form: a null Status is no badge. The total and what has
     * been paid are the page's subtitle ({@link #subtitle()}).
     */
    @ReadOnly
    @HiddenInCreate
    Status pendingBadge;

    /** «Total 1.431,12 EUR (5 noches) · Pagado 0,00 EUR» — the page's subtitle; null in creation. */
    @io.mateu.uidl.annotations.Hidden
    String amounts;

    @Override
    public String subtitle() {
        return amounts;
    }

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
    /** Opera's reservation: in the overview, where it is looked for. */
    @ReadOnly
    @HiddenInCreate
    @Label("Opera reservation")
    String pmsReservationId;

    /** Wide enough for an email address on one line. */
    @Section(value = "Holder", panelWidth = PanelWidth.MEDIUM)
    @NotEmpty
    String holderFirstName;
    @NotEmpty
    String holderLastName;
    String holderEmail;
    String holderPhone;
    String holderNationality;

    /**
     * On the booking's page, each room with its people, together: two lists side by side made the
     * reader match guests to rooms by line number. The editor keeps the two lists below.
     */
    @Section(value = "Rooms and guests", panelWidth = PanelWidth.MEDIUM)
    @HiddenInCreate
    @HiddenInEditor
    @Label("")
    @Colspan(2)
    Callable<Component> roomsAndGuests = this::roomsAndGuests;
    /** The comments, on the page, under the rooms and guests; the editor edits them in their own section. */
    @HiddenInCreate
    @HiddenInEditor
    @ReadOnly
    @Label("Comments")
    @Colspan(2)
    String commentsOnPage;

    // The lists show a few columns each and open a row in a modal, where all of its fields fit: a
    // row edited beside the list had too many fields for the space left to it. Only in the editor:
    // the page shows them together, above.
    @Section("Rooms")
    @HiddenInView
    @DetailFormCustomisation(position = FormPosition.modal)
    @Colspan(2)
    List<RoomViewModel> rooms;

    @Section("Guests")
    @HiddenInView
    @DetailFormCustomisation(position = FormPosition.modal)
    @Colspan(2)
    List<GuestViewModel> guests;

    /**
     * On the booking's page, each payment as a card — like each room above: type and method, the
     * amount as a badge, the date and the reference under it. No table: a grid in a fold read as
     * a form. The editor keeps the list below.
     */
    @Section(value = "Payments", panelWidth = PanelWidth.MEDIUM)
    @HiddenInCreate
    @HiddenInEditor
    @Label("")
    @Colspan(2)
    Callable<Component> paymentsOnPage = this::paymentCards;

    @HiddenInView
    @DetailFormCustomisation(position = FormPosition.modal)
    @Colspan(2)
    List<PaymentViewModel> payments;

    @Section("Comments")
    @HiddenInView
    @Stereotype(FieldStereotype.textarea)
    @Colspan(2)
    String comments;

    @Section("Cancellation")
    @ReadOnly
    @HiddenInCreate
    String cancellation;

    /** The record's bookkeeping, in its own narrow fold on the page (and its own section in the editor). */
    @Section(value = "Tracking", panelWidth = PanelWidth.NARROW)
    @ReadOnly
    @HiddenInCreate
    String id;
    @ReadOnly
    @HiddenInCreate
    Long version;
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
    @Section(value = "In other systems", panelWidth = PanelWidth.WIDE)
    @HiddenInCreate
    @HiddenInEditor
    @Label("")
    @Colspan(2)
    Callable<Component> otherSystems = this::otherSystems;

    /** Who did what with the booking, and when: here, through the console's agent, and at the front office. */
    @Section(value = "History", panelWidth = PanelWidth.MEDIUM)
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
        var stored = queryService.getById(id).orElseThrow(() -> new NoSuchElementException("Booking " + id + " not found"));
        if (!stored.hotelCode().equals(hotelCode)) {
            throw new IllegalArgumentException("A booking cannot move to another hotel: cancel it and create a new one");
        }
        var changed = updateBookingUseCase.handle(new UpdateBookingCommand(id, request()));
        var paid = BookingRequests.registerNewPayments(registerPaymentUseCase, id, payments);
        if (!changed && !paid) {
            // nothing to save: say so instead of "saved" (Mateu's crud reads this attribute)
            httpRequest.setAttribute("mateu.savedMessage", "Sin cambios");
        }
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

    /** Each room line — type, occupancy, price — and the people in it, one line each. */
    Component roomsAndGuests() {
        var byLine = new java.util.LinkedHashMap<Integer, List<GuestViewModel>>();
        (guests == null ? List.<GuestViewModel>of() : guests)
                .forEach(g -> byLine.computeIfAbsent(g.roomLine(), l -> new java.util.ArrayList<>()).add(g));
        return io.mateu.uidl.data.StatusList.builder().compact(true).frameless(true).style("width: 100%;")
                .items((rooms == null ? List.<RoomViewModel>of() : rooms).stream().map(room -> {
                    int line = room.line() == null ? 0 : room.line();
                    var people = byLine.getOrDefault(line, List.of());
                    var children = room.childrenAges() == null ? 0 : room.childrenAges().size();
                    return io.mateu.uidl.data.StatusItem.builder()
                            .id("room-" + line)
                            .title("Room " + line + " · " + room.roomTypeCode())
                            .description(room.adults() + (room.adults() == 1 ? " adult" : " adults")
                                    + (children == 0 ? "" : " · " + children + (children == 1 ? " child" : " children"))
                                    + (room.boardCode() == null ? "" : " · " + room.boardCode()))
                            .status(room.total() == null ? "" : room.total().toPlainString())
                            .statusColor("neutral")
                            .lines(people.isEmpty() ? List.of("No guests named yet")
                                    : people.stream().map(g -> g.firstName() + " " + g.lastName()
                                    + (g.type() == null ? "" : " · " + g.type())).toList())
                            .build();
                }).toList())
                .build();
    }

    /** Each payment as a card: «Deposit · VISA», the amount as its badge, date and reference below. */
    Component paymentCards() {
        var list = payments == null ? List.<PaymentViewModel>of() : payments;
        if (list.isEmpty()) {
            return io.mateu.uidl.data.Text.builder().text("Sin pagos").build();
        }
        var day = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        var index = new java.util.concurrent.atomic.AtomicInteger();
        return io.mateu.uidl.data.StatusList.builder().compact(true).frameless(true).style("width: 100%;")
                .items(list.stream().map(p -> {
                    var lines = new java.util.ArrayList<String>();
                    if (p.date() != null) lines.add(day.format(p.date()));
                    if (p.reference() != null && !p.reference().isBlank()) lines.add(p.reference());
                    return io.mateu.uidl.data.StatusItem.builder()
                            .id("payment-" + (p.paymentId() != null ? p.paymentId() : index.incrementAndGet()))
                            .title(paymentTitleOf(p))
                            .status(p.amount() == null ? "" : p.amount().toPlainString())
                            .statusColor("neutral")
                            .lines(lines)
                            .build();
                }).toList())
                .build();
    }

    static String paymentTitleOf(PaymentViewModel p) {
        var type = p.type() == null ? "Payment" : p.type().name();
        return p.methodCode() == null || p.methodCode().isBlank() ? type : type + " · " + p.methodCode();
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
        pendingBadge = pendingBadgeOf(booking.totalAmount(), booking.paidAmount(), booking.currency());
        amounts = amountsOf(booking.totalAmount(), booking.paidAmount(), booking.currency(), booking.nights());
        comments = booking.comments();
        commentsOnPage = comments;
        cancellation = booking.cancellation() != null
                ? booking.cancellation().reasonCode() + " at " + TIMESTAMP.format(booking.cancellation().cancelledAt())
                : null;
        created = TIMESTAMP.format(booking.created());
        updated = TIMESTAMP.format(booking.updated());
        return this;
    }

    /** «Pendiente 1.431,12 EUR» in amber while something is left to pay; «Pagado» in green once nothing is. */
    static Status pendingBadgeOf(java.math.BigDecimal total, java.math.BigDecimal paid, String currency) {
        var pending = nz(total).subtract(nz(paid)).max(java.math.BigDecimal.ZERO);
        return pending.signum() > 0
                ? new Status(StatusType.WARNING, "Pendiente " + money(pending, currency))
                : new Status(StatusType.SUCCESS, "Pagado");
    }

    /** «Total 1.431,12 EUR (5 noches) · Pagado 0,00 EUR». */
    static String amountsOf(java.math.BigDecimal total, java.math.BigDecimal paid, String currency, long nights) {
        return "Total " + money(total, currency) + " (" + nights + (nights == 1 ? " noche" : " noches") + ")"
                + " · Pagado " + money(paid, currency);
    }

    static String money(java.math.BigDecimal amount, String currency) {
        var format = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("es-ES"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        format.setGroupingUsed(true);
        return format.format(nz(amount)) + (currency == null ? "" : " " + currency);
    }

    private static java.math.BigDecimal nz(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }

    @Override
    public String toString() {
        return id != null ? id + " · " + holderFirstName + " " + holderLastName : "New booking";
    }
}
