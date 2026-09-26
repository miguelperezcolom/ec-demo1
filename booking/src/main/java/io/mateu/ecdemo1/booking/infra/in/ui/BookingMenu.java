package io.mateu.ecdemo1.booking.infra.in.ui;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;
import io.mateu.ecdemo1.booking.infra.in.ui.pages.BookingCrudOrchestrator;
import io.mateu.ecdemo1.booking.infra.in.ui.pages.NewBookingWizard;

public class BookingMenu {

    @Menu
    BookingCrudOrchestrator bookings;

    /** A booking made a step at a time; New in the bookings list opens it too. */
    @Menu
    @Label("New booking")
    NewBookingWizard newBooking;


}
