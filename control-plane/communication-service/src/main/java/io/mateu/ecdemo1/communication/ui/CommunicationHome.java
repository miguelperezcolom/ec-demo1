package io.mateu.ecdemo1.communication.ui;

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

    /** One section, so the shell's RemoteMenu can name it: "Notifications". */
    @Menu
    CommunicationMenu notifications;
}
