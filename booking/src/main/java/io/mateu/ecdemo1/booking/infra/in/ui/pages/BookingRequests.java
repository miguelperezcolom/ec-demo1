package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;
import io.mateu.ecdemo1.booking.application.usecases.booking.RoomRequest;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Guest;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Where UI-shaped data becomes a request, shared by the booking form and the new-booking wizard.
 *
 * <p>Collections arrive null, not empty, when a form left them untouched — see ContentViewModel in
 * the content service for why. They are normalised here.
 */
final class BookingRequests {

    static BookingRequest of(String channelCode, String partnerCode, String externalReference,
                             LocalDate arrival, LocalDate departure, Holder holder,
                             List<RoomViewModel> rooms, List<GuestViewModel> guests, String comments) {
        var roomList = rooms != null ? rooms : List.<RoomViewModel>of();
        var guestList = guests != null ? guests : List.<GuestViewModel>of();
        var misplaced = guestOutsideTheRooms(roomList, guestList);
        if (misplaced != null) {
            throw new IllegalArgumentException(misplaced);
        }
        return new BookingRequest(
                channelCode,
                partnerCode,
                externalReference,
                arrival,
                departure,
                holder,
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

    /** Why a guest cannot be placed — the room line they name is not one of the booking's — or null. */
    static String guestOutsideTheRooms(List<RoomViewModel> rooms, List<GuestViewModel> guests) {
        var roomCount = rooms != null ? rooms.size() : 0;
        if (guests == null) {
            return null;
        }
        return guests.stream().filter(g -> g.roomLine() > roomCount).findFirst()
                .map(g -> "Guest %s %s is in room %d, and the booking has %d room(s)"
                        .formatted(g.firstName(), g.lastName(), g.roomLine(), roomCount))
                .orElse(null);
    }

    /** Registers the payments not registered yet: those with no id. A registered one cannot change. */
    static void registerNewPayments(RegisterPaymentUseCase useCase, String bookingId, List<PaymentViewModel> payments) {
        if (payments == null) {
            return;
        }
        payments.stream().filter(p -> p.paymentId() == null || p.paymentId().isBlank()).forEach(p ->
                useCase.handle(new RegisterPaymentCommand(
                        bookingId, p.type(), p.methodCode(), p.amount(), p.date(), p.reference())));
    }

    private BookingRequests() {
    }
}
