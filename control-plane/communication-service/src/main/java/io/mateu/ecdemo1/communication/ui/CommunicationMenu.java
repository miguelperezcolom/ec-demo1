package io.mateu.ecdemo1.communication.ui;

import io.mateu.ecdemo1.communication.ui.pages.NotificationsPage;
import io.mateu.ecdemo1.communication.ui.pages.RecipientsCrud;
import io.mateu.uidl.annotations.Menu;

public class CommunicationMenu {

    @Menu
    NotificationsPage history;
    @Menu
    RecipientsCrud recipients;
}
