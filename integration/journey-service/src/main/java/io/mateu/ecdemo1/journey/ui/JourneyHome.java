package io.mateu.ecdemo1.journey.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import org.springframework.stereotype.Service;

/**
 * A booking's journey across the chain, federated into the DATA plane's shells: opened from the
 * booking ("Ver recorrido"), from Clientes and from the front office's stay. The shells declare it
 * hidden — it is a place one is taken to, not a section of the bar.
 *
 * <p>The path here, the gateway's route for the data plane's hosts and the shells' RemoteMenu have
 * to agree.
 */
@UI("/_journey")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Recorrido")
@Logo("/images/riu.svg")
@Style(StyleConstants.FULL_WIDTH)
@Service
public class JourneyHome {

    /** The routes hang from the field name: /journey/bookings, /journey/bookings/{locator}. */
    @Menu
    @Label("Recorrido")
    JourneyMenu journey;
}
