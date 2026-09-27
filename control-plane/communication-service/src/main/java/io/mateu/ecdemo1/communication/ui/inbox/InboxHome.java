package io.mateu.ecdemo1.communication.ui.inbox;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import org.springframework.stereotype.Service;

/**
 * A person's inbox, federated into every shell — the data plane's and the control console's alike:
 * what waits for them, whichever service it came from.
 */
@UI("/_inbox")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Inbox")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class InboxHome {

    @Menu
    InboxMenu inbox;
}
