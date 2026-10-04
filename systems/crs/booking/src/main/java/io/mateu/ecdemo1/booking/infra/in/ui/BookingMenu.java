package io.mateu.ecdemo1.booking.infra.in.ui;

import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;
import io.mateu.ecdemo1.booking.infra.in.ui.pages.BookingCrudOrchestrator;
import io.mateu.ecdemo1.booking.infra.in.ui.pages.NewBookingWizard;
import io.mateu.ecdemo1.booking.infra.in.ui.pages.catalog.CatalogueMenu;

public class BookingMenu {

    @Menu
    BookingCrudOrchestrator bookings;

    /**
     * A booking made a step at a time. Not on the menu: New in the bookings list opens it. Hidden,
     * not removed, so its route still resolves — that is where New goes, and a reload lands back on it.
     */
    @Menu
    @Hidden
    @Label("New booking")
    NewBookingWizard newBooking;

    /** The CRS's catalogue: the rate plans of each hotel, and New to open one (demo flow 8). */
    @Menu
    @Label("Catalogue")
    CatalogueMenu catalogue;


}
