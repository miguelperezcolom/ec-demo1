package io.mateu.ecdemo1.customerhistory.infra.in.ui;

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
 * The customers' stay history, on the data plane's console: for the head office (marketing, CRM), not
 * the desk. One top section, whose label the shells' RemoteMenu repeats ("Historial de clientes"); the
 * routes hang from the field names: /history/search.
 */
@UI("/_history")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Historial de clientes")
@Logo("/images/riu.svg")
@Style(StyleConstants.FULL_WIDTH)
@Service
public class HistoryHome {

    @Menu
    @Label("Historial de clientes")
    HistoryMenu history;
}
