package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingUseCase;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonColor;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.Dialog;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.EmbeddedView;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.UserTrigger;
import io.mateu.uidl.interfaces.ButtonsSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.TitleSupplier;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Cancels one booking or several, for one reason: a booking is cancelled, never deleted — the
 * cancellation is what reaches Opera and the front office. Opened in a dialog from the bookings list
 * (the selected rows) or from a booking.
 *
 * <p>No show is not offered: that cancellation is the CRS's, when the hotel reports one.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class BookingCancellationForm implements TitleSupplier, ButtonsSupplier, VisibilitySupplier, OptionsSupplier {

    /** Shown only for several: for one, the dialog's header names it. */
    @ReadOnly
    @Label("Bookings")
    @Getter
    String bookingIds;

    /** A handful of reasons: all of them at once, not a search. */
    @Label("Reason")
    @Stereotype(FieldStereotype.select)
    @Getter
    String cancellationReasonCode;

    /** Where to go once done: the list the bookings were picked from, or the booking itself. */
    @Hidden
    @Getter
    String returnTo;

    final CancelBookingUseCase cancelBookingUseCase;
    final BookingQueryService queryService;
    final CrsCatalog catalog;

    /** The dialog asking why, for these bookings. */
    Dialog dialogFor(List<String> ids, String returnTo) {
        this.bookingIds = String.join(", ", ids);
        this.returnTo = returnTo;
        // The form's own title heads it (see title()): a dialog header as well said it twice.
        return Dialog.builder()
                .width("32rem")
                // Embedded, not a ModelViewComponent: that one draws the form as part of the page
                // behind the dialog, so its state never travelled and its buttons went to the
                // booking. Embedded, it is a component of its own, with its own actions, and its
                // state is this object as it is serialised — hence the getters on the fields.
                .content(new EmbeddedView(this))
                .build();
    }

    @Override
    public String title() {
        var ids = ids();
        return ids.size() == 1 ? "Cancel booking " + ids.get(0) : "Cancel " + ids.size() + " bookings";
    }

    /**
     * At the foot of the dialog, worded for what is being cancelled: one booking or several. The
     * destructive one first and in red; the way out beside it.
     */
    @Override
    public Collection<UserTrigger> buttons() {
        var several = ids().size() > 1;
        return List.of(
                Button.builder().label(several ? "Cancel bookings" : "Cancel booking")
                        .actionId("cancelBookings").buttonStyle(ButtonStyle.primary).color(ButtonColor.error).build(),
                Button.builder().label(several ? "Keep them" : "Keep it")
                        .actionId("keep").buttonStyle(ButtonStyle.tertiary).build());
    }

    @Override
    public boolean isHidden(String memberName, HttpRequest httpRequest) {
        return "bookingIds".equals(memberName) && ids().size() < 2;
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return BookingCancellationForm.class.equals(formType) && "cancellationReasonCode".equals(fieldName);
    }

    /**
     * The reasons the CRS accepts for these bookings: each hotel has its own list, and a reason of
     * another hotel is refused on cancelling — so only those every booking's hotel knows, in the
     * first hotel's order. No show is left out: it is not a choice here.
     */
    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        if (!"cancellationReasonCode".equals(fieldName)) {
            return List.of();
        }
        var hotels = ids().stream().map(queryService::getById).flatMap(Optional::stream)
                .map(BookingDto::hotelCode).distinct().toList();
        if (hotels.isEmpty()) {
            return List.of();
        }
        var reasons = new ArrayList<>(catalog.codes(hotels.get(0)).cancellationReasons());
        for (var hotel : hotels.subList(1, hotels.size())) {
            var known = catalog.codes(hotel).cancellationReasons().stream().map(CrsCatalog.Code::code).toList();
            reasons.removeIf(reason -> !known.contains(reason.code()));
        }
        return reasons.stream().filter(reason -> !NO_SHOW.equals(reason.code()))
                .map(reason -> new Option(reason.code(), reason.code() + " — " + reason.name()))
                .toList();
    }

    /** The CRS's own cancellation when the hotel reports a no show — never picked by hand. */
    static final String NO_SHOW = "NOS";

    @Action
    public Object cancelBookings() {
        if (cancellationReasonCode == null || cancellationReasonCode.isBlank()) {
            return Message.error("Pick the reason for the cancellation");
        }
        var outcome = cancel(ids());
        return List.of(outcome.message(), UICommand.closeModal(), UICommand.navigateTo(returnTo));
    }

    @Action
    public UICommand keep() {
        return UICommand.closeModal();
    }

    /**
     * Cancels each booking on its own: one that is cancelled already, or that the CRS refuses to
     * cancel, is reported and does not stop the rest.
     */
    Outcome cancel(List<String> ids) {
        var cancelled = new ArrayList<String>();
        var alreadyCancelled = new ArrayList<String>();
        var refused = new ArrayList<String>();
        for (var id : ids) {
            var booking = queryService.getById(id);
            if (booking.isEmpty()) {
                refused.add(id + " (not found)");
                continue;
            }
            if (booking.get().status() == BookingStatus.Cancelled) {
                alreadyCancelled.add(id);
                continue;
            }
            try {
                cancelBookingUseCase.handle(new CancelBookingCommand(id, cancellationReasonCode));
                cancelled.add(id);
            } catch (RuntimeException e) {
                refused.add(id + " (" + e.getMessage() + ")");
            }
        }
        return new Outcome(cancelled, alreadyCancelled, refused);
    }

    List<String> ids() {
        return bookingIds == null ? List.of()
                : Arrays.stream(bookingIds.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    record Outcome(List<String> cancelled, List<String> alreadyCancelled, List<String> refused) {

        Message message() {
            var parts = new ArrayList<String>();
            if (!cancelled.isEmpty()) {
                parts.add("Cancelled: " + String.join(", ", cancelled));
            }
            if (!alreadyCancelled.isEmpty()) {
                parts.add("Already cancelled: " + String.join(", ", alreadyCancelled));
            }
            if (!refused.isEmpty()) {
                parts.add("Not cancelled: " + String.join(", ", refused));
            }
            var text = parts.isEmpty() ? "Nothing to cancel" : String.join(". ", parts);
            return refused.isEmpty() && !cancelled.isEmpty() ? Message.success(text)
                    : cancelled.isEmpty() ? Message.error(text) : Message.warning(text);
        }
    }
}
