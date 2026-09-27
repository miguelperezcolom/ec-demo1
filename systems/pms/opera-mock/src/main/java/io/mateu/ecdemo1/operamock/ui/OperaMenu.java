package io.mateu.ecdemo1.operamock.ui;

import io.mateu.ecdemo1.operamock.ui.pages.CallsPage;
import io.mateu.ecdemo1.operamock.ui.pages.FaultsPage;
import io.mateu.ecdemo1.operamock.ui.pages.PropertiesPage;
import io.mateu.ecdemo1.operamock.ui.pages.ProfilesPage;
import io.mateu.ecdemo1.operamock.ui.pages.ReservationsPage;
import io.mateu.uidl.annotations.Menu;

public class OperaMenu {

    @Menu
    ReservationsPage reservations;
    @Menu
    ProfilesPage profiles;
    @Menu
    CallsPage calls;
    @Menu
    FaultsPage faults;
    @Menu
    PropertiesPage properties;
}
