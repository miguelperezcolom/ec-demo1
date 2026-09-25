package io.mateu.ecdemo1.integrations.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import org.springframework.stereotype.Service;

/** The integrations' screens, federated into the control console (HLA: services bring their own UI). */
@UI("/_integrations")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Integrations")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class IntegrationsHome {

    @Menu
    IntegrationsMenu integrations;
}
