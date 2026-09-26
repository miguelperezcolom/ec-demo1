package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.wizard.Wizard;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.WizardCompletionAction;
import io.mateu.uidl.annotations.WizardProgress;
import io.mateu.uidl.annotations.WizardProgressStyle;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A new booking, one step at a time: the stay and its holder, the rooms, the guests, the payments,
 * and a summary to read before it is created. Editing a booking is still the booking form.
 *
 * <p>Each step validates its own fields before the next one opens; what a step cannot check alone —
 * a channel that needs a partner, a guest in a room the booking does not have — is checked when it is
 * left. Creating goes through the same use cases as the form, so prices and rules are the CRS's.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("New booking")
// The default page style caps the width but does not take it: a form of fixed-width fields then
// shrinks the page to their width, a narrow column in the middle. Taking the width fixes that.
@Style("width: 100%; max-width: 1100px; margin: auto;")
@WizardProgress(WizardProgressStyle.STEPS)
public class NewBookingWizard extends Wizard {

    /** Where the wizard is mounted: Call center (the field in BookingHome) → newBooking (BookingMenu). */
    public static final String ROUTE = "/booking/newBooking";

    @Label("Stay")
    StayStep stay;

    @Label("Rooms")
    RoomsStep rooms;

    @Label("Guests")
    GuestsStep guests;

    @Label("Payments")
    PaymentsStep payments;

    @Label("Summary")
    SummaryStep summary;

    @Label("Created")
    CreatedStep created;

    final CreateBookingUseCase createBookingUseCase;
    final RegisterPaymentUseCase registerPaymentUseCase;
    final CrsCatalog catalog;

    @Override
    public Object handleAction(String actionId, HttpRequest httpRequest) {
        if ("next".equals(actionId)) {
            var problem = problemLeaving(currentStepField().getName());
            if (problem != null) {
                return Message.error(problem);
            }
        }
        var result = super.handleAction(actionId, httpRequest);
        if ("summary".equals(currentStepField().getName())) {
            summary = summarise();
        }
        return result;
    }

    /** What stops the current step from being left — beyond its fields' own validations — or null. */
    String problemLeaving(String step) {
        return switch (step) {
            case "stay" -> {
                if (stay == null) {
                    yield null;
                }
                if (stay.arrival() != null && stay.departure() != null && !stay.departure().isAfter(stay.arrival())) {
                    yield "The departure has to be after the arrival";
                }
                var channel = catalog.channels().stream().filter(c -> c.code().equals(stay.channelCode())).findFirst();
                if (channel.isPresent() && channel.get().requiresPartner()
                        && (stay.partnerCode() == null || stay.partnerCode().isBlank())) {
                    yield "Channel %s sells through a partner: give the partner's code".formatted(channel.get().name());
                }
                yield null;
            }
            case "rooms" -> rooms == null || rooms.rooms() == null || rooms.rooms().isEmpty()
                    ? "Add at least one room" : null;
            case "guests" -> BookingRequests.guestOutsideTheRooms(
                    rooms != null ? rooms.rooms() : null, guests != null ? guests.guests() : null);
            default -> null;
        };
    }

    SummaryStep summarise() {
        var roomList = rooms != null && rooms.rooms() != null ? rooms.rooms() : List.<RoomViewModel>of();
        var guestList = guests != null && guests.guests() != null ? guests.guests() : List.<GuestViewModel>of();
        var paymentList = payments != null && payments.payments() != null ? payments.payments() : List.<PaymentViewModel>of();
        var hotel = catalog.hotels().stream().filter(h -> h.code().equals(stay.hotelCode()))
                .map(CrsCatalog.Hotel::name).findFirst().orElse(stay.hotelCode());
        var roomLines = new StringBuilder();
        for (int i = 0; i < roomList.size(); i++) {
            var r = roomList.get(i);
            roomLines.append("%d. %s · %s · %s · %d adult(s)%s%n".formatted(i + 1, r.roomTypeCode(), r.ratePlanCode(),
                    r.boardCode(), r.adults(),
                    r.childrenAges() == null || r.childrenAges().isEmpty() ? "" : " · children " + r.childrenAges()));
        }
        return new SummaryStep(
                "%s · %s → %s · channel %s%s".formatted(hotel, stay.arrival(), stay.departure(), stay.channelCode(),
                        stay.partnerCode() == null || stay.partnerCode().isBlank() ? "" : " · partner " + stay.partnerCode()),
                "%s %s%s".formatted(stay.holderFirstName(), stay.holderLastName(),
                        stay.holderEmail() == null || stay.holderEmail().isBlank() ? "" : " · " + stay.holderEmail()),
                roomLines.toString().strip(),
                guestList.isEmpty() ? "None yet" : guestList.stream()
                        .map(g -> "Room %d: %s %s (%s)".formatted(g.roomLine(), g.firstName(), g.lastName(), g.type()))
                        .collect(Collectors.joining("\n")),
                paymentList.isEmpty() ? "None" : paymentList.stream()
                        .map(p -> "%s %s%s".formatted(p.type(), p.amount(), p.methodCode() == null ? "" : " · " + p.methodCode()))
                        .collect(Collectors.joining("\n")));
    }

    /** Creates the booking and opens it; the prices are worked out by the CRS as it is created. */
    @WizardCompletionAction
    @Label("Create booking")
    public Object createBooking() {
        var request = BookingRequests.of(stay.channelCode(), stay.partnerCode(), stay.externalReference(),
                stay.arrival(), stay.departure(),
                new Holder(stay.holderFirstName(), stay.holderLastName(), stay.holderEmail(), stay.holderPhone(),
                        stay.holderNationality()),
                rooms != null ? rooms.rooms() : null, guests != null ? guests.guests() : null, stay.comments());
        var id = createBookingUseCase.handle(new CreateBookingCommand(stay.hotelCode(), request));
        BookingRequests.registerNewPayments(registerPaymentUseCase, id, payments != null ? payments.payments() : null);
        return List.of(new Message("Booking " + id + " created"), UICommand.navigateTo("/booking/bookings/" + id));
    }
}
