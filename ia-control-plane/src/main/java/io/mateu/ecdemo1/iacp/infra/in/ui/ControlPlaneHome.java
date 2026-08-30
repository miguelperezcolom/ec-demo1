package io.mateu.ecdemo1.iacp.infra.in.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.UI;

/**
 * The control plane's UI, hosted by the control shell as a remote menu at {@code /_ia-cp}.
 *
 * <p>That path has to match in three places — this annotation, the {@code RemoteMenu} in the
 * control shell, and the gateway's route — or the menu renders empty with no error anywhere.
 */
@UI("/_ia-cp")
@PageTitle("IA control plane")
@Style(StyleConstants.FULL_WIDTH)
public class ControlPlaneHome {

    // The label the control console shows for this section. "IA" rather than the field name's
    // "Catalogues": the console is an admin console now, and this is its AI half — the other being
    // Users, mounted alongside it by the control shell.
    @Menu
    @Label("IA")
    ControlPlaneMenu catalogues;

}
