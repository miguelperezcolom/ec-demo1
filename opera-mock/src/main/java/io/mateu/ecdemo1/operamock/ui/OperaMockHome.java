package io.mateu.ecdemo1.operamock.ui;

import io.mateu.ecdemo1.operamock.ui.pages.CallsPage;
import io.mateu.ecdemo1.operamock.ui.pages.FaultsPage;
import io.mateu.ecdemo1.operamock.ui.pages.ProfilesPage;
import io.mateu.ecdemo1.operamock.ui.pages.ReservationsPage;
import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.*;
import org.springframework.stereotype.Service;

/** What reached "Opera": the double's own screens. */
@UI("/_opera-mock")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Opera (double)")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class OperaMockHome {

    @Menu
    ReservationsPage reservations;
    @Menu
    ProfilesPage profiles;
    @Menu
    CallsPage calls;
    @Menu
    FaultsPage faults;
}
