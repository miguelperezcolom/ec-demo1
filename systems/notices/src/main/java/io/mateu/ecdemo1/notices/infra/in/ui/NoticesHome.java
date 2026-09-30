package io.mateu.ecdemo1.notices.infra.in.ui;

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
 * The reception notices, on the data plane's console (ec1, rw): the business's, not the platform's —
 * what the desk must know of a customer, a reservation or a partner. The shells' menu entry is named
 * the same ({@code RemoteMenu avisos}), so a notice is at /avisos/recepcion/...
 */
@UI("/_notices")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Avisos")
@Logo("/images/riu.svg")
@Style(StyleConstants.FULL_WIDTH)
@Service
public class NoticesHome {

    @Menu
    @Label("Avisos")
    NoticesMenu avisos;
}
