package io.mateu.ecdemo1.communication.ui;

import io.mateu.ecdemo1.communication.ui.pages.NotificationsPage;
import io.mateu.ecdemo1.communication.ui.pages.RecipientsCrud;
import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.*;
import org.springframework.stereotype.Service;

@UI("/_communication")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Notifications")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class CommunicationHome {

    @Menu
    NotificationsPage notifications;
    @Menu
    RecipientsCrud recipients;
}
