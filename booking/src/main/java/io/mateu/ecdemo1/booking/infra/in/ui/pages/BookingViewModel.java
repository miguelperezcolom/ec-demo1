package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;
import io.mateu.ecdemo1.booking.application.usecases.booking.RoomRequest;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.confirm.ConfirmBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.confirm.ConfirmBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.update.UpdateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.update.UpdateBookingUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Guest;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;
import io.mateu.ecdemo1.booking.infra.in.ui.suppliers.CatalogLookup;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.MasterDetail;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
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
import java.util.stream.IntStream;

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
public class BookingViewModel implements Identifiable {

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

    @Section("Rooms")
    @MasterDetail(minHeightWhenDetailVisible = "26rem;")
    @Colspan(2)
    List<RoomViewModel> rooms;

    @Section("Guests")
    @MasterDetail(minHeightWhenDetailVisible = "26rem;")
    @Colspan(2)
    List<GuestViewModel> guests;

    @Section("Payments")
    @MasterDetail(minHeightWhenDetailVisible = "20rem;")
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
    @HiddenInCreate
    @Lookup(search = CatalogLookup.class, label = CatalogLookup.class)
    String cancellationReasonCode;
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

    final CreateBookingUseCase createBookingUseCase;
    final UpdateBookingUseCase updateBookingUseCase;
    final ConfirmBookingUseCase confirmBookingUseCase;
    final CancelBookingUseCase cancelBookingUseCase;
    final RegisterPaymentUseCase registerPaymentUseCase;
    final BookingQueryService queryService;

    public String create(HttpRequest httpRequest) {
        var newId = createBookingUseCase.handle(new CreateBookingCommand(hotelCode, request()));
        registerNewPayments(newId);
        return newId;
    }

    public void save(HttpRequest httpRequest) {
        var stored = queryService.getById(id).orElseThrow(() -> new NoSuchElementException("Booking not found: " + id));
        if (!stored.hotelCode().equals(hotelCode)) {
            throw new IllegalArgumentException("A booking cannot move to another hotel: cancel it and create a new one");
        }
        updateBookingUseCase.handle(new UpdateBookingCommand(id, request()));
        registerNewPayments(id);
    }

    @Toolbar
    @Action
    public Object confirmBooking(HttpRequest httpRequest) {
        confirmBookingUseCase.handle(new ConfirmBookingCommand(requireSaved()));
        return reloaded("Booking confirmed");
    }

    @Toolbar
    @Action(confirmationRequired = true,
            confirmationTitle = "Cancel this booking?",
            confirmationMessage = "A cancelled booking cannot be modified or confirmed again.")
    public Object cancelBooking(HttpRequest httpRequest) {
        if (cancellationReasonCode == null || cancellationReasonCode.isBlank()) {
            return Message.error("Pick a cancellation reason first, in the Cancellation section");
        }
        cancelBookingUseCase.handle(new CancelBookingCommand(requireSaved(), cancellationReasonCode));
        return reloaded("Booking cancelled");
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

    /**
     * Collections arrive null, not empty, when the form left them untouched — see ContentViewModel
     * in the content service for why. They are normalised here, where UI-shaped data becomes a
     * request.
     */
    private BookingRequest request() {
        var roomList = rooms != null ? rooms : List.<RoomViewModel>of();
        var guestList = guests != null ? guests : List.<GuestViewModel>of();
        guestList.stream().filter(g -> g.roomLine() > roomList.size()).findFirst().ifPresent(g -> {
            throw new IllegalArgumentException("Guest %s %s is in room %d, and the booking has %d room(s)"
                    .formatted(g.firstName(), g.lastName(), g.roomLine(), roomList.size()));
        });
        return new BookingRequest(
                channelCode,
                partnerCode,
                externalReference,
                arrival,
                departure,
                new Holder(holderFirstName, holderLastName, holderEmail, holderPhone, holderNationality),
                IntStream.range(0, roomList.size()).mapToObj(i -> {
                    var room = roomList.get(i);
                    return new RoomRequest(room.roomTypeCode(), room.ratePlanCode(), room.boardCode(),
                            room.adults(), room.childrenAges(),
                            guestList.stream().filter(g -> g.roomLine() == i + 1)
                                    .map(g -> new Guest(g.firstName(), g.lastName(), g.type(), g.age(),
                                            g.birthDate(), g.nationality(), g.documentType(), g.documentNumber()))
                                    .toList());
                }).toList(),
                comments);
    }

    private void registerNewPayments(String bookingId) {
        if (payments == null) {
            return;
        }
        payments.stream().filter(p -> p.paymentId() == null || p.paymentId().isBlank()).forEach(p ->
                registerPaymentUseCase.handle(new RegisterPaymentCommand(
                        bookingId, p.type(), p.methodCode(), p.amount(), p.date(), p.reference())));
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
        cancellationReasonCode = booking.cancellation() != null ? booking.cancellation().reasonCode() : null;
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
