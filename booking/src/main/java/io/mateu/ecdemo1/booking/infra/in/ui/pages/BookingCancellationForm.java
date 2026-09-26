package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.infra.in.ui.suppliers.CatalogLookup;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.Dialog;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.ModelViewComponent;
import io.mateu.uidl.data.UICommand;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
@Title("Cancel bookings")
public class BookingCancellationForm {

    @ReadOnly
    @Label("Bookings")
    String bookingIds;

    @Label("Reason")
    @Lookup(search = CatalogLookup.class, label = CatalogLookup.class)
    String cancellationReasonCode;

    /** Where to go once done: the list the bookings were picked from, or the booking itself. */
    @Hidden
    String returnTo;

    final CancelBookingUseCase cancelBookingUseCase;
    final BookingQueryService queryService;

    /** The dialog asking why, for these bookings. */
    Dialog dialogFor(List<String> ids, String returnTo) {
        this.bookingIds = String.join(", ", ids);
        this.returnTo = returnTo;
        return Dialog.builder()
                .headerTitle(ids.size() == 1 ? "Cancel booking " + ids.get(0) : "Cancel " + ids.size() + " bookings")
                .width("32rem")
                .content(new ModelViewComponent(this))
                .build();
    }

    @Toolbar
    @Label("Cancel them")
    public Object cancelBookings() {
        if (cancellationReasonCode == null || cancellationReasonCode.isBlank()) {
            return Message.error("Pick the reason for the cancellation");
        }
        var outcome = cancel(ids());
        return List.of(outcome.message(), UICommand.closeModal(), UICommand.navigateTo(returnTo));
    }

    @Toolbar
    @Label("Keep them")
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
