package io.mateu.ecdemo1.journey.ui;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/** The one entry: the bookings with a journey, each opening its own. */
public class JourneyMenu {

    @Menu
    @Label("Recorridos de reservas")
    JourneyListPage bookings;
}
