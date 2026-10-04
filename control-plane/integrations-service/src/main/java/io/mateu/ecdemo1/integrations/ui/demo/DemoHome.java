package io.mateu.ecdemo1.integrations.ui.demo;

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
 * The demo's administration, federated into the control console: reset it to zero (the engine's
 * reset-demo) and simulate an Opera outage. Administrators only — the gateway lets /_demo through on
 * the control host to ai-admin alone.
 */
@UI("/_demo")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Demo")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class DemoHome {

    @Menu
    DemoMenu demo;
}
