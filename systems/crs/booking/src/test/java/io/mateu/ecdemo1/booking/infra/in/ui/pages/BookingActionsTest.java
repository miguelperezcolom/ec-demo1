package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A booking offers only the actions that can do something in the state it is in. */
class BookingActionsTest {

    static BookingViewModel booking(String id, String state) {
        var vm = new BookingViewModel(null, null, null, null, null, null, null);
        vm.id = id;
        if (state != null) {
            vm.status = new Status(StatusType.NONE, state);
        }
        return vm;
    }

    @Test
    void aPendingBookingCanBeConfirmedOrCancelled() {
        var vm = booking("79RE8S", "Pending");
        assertThat(vm.isHidden("confirmBooking", null)).isFalse();
        assertThat(vm.isHidden("cancelBooking", null)).isFalse();
    }

    @Test
    void aConfirmedBookingIsNotConfirmedAgain() {
        var vm = booking("79RE8S", "Confirmed");
        assertThat(vm.isHidden("confirmBooking", null)).isTrue();
        assertThat(vm.isHidden("cancelBooking", null)).isFalse();
    }

    @Test
    void aCancelledBookingIsNeitherConfirmedNorCancelledAgain() {
        var vm = booking("DPUQKZ", "Cancelled");
        assertThat(vm.isHidden("confirmBooking", null)).isTrue();
        assertThat(vm.isHidden("cancelBooking", null)).isTrue();
    }

    @Test
    void aNewBookingIsSavedBeforeAnything() {
        var vm = booking(null, null);
        assertThat(vm.isHidden("confirmBooking", null)).isTrue();
        assertThat(vm.isHidden("cancelBooking", null)).isTrue();
        assertThat(vm.isHidden("verRecorrido", null)).isTrue();
    }
}
