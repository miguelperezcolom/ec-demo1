package io.mateu.ecdemo1.integrations.ui.usage;

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
 * The external APIs' usage, federated into every shell — the data plane's and the control console's —
 * behind the KPI tiles of their home pages. Its own base URL, not
 * {@code /_integrations}: that one is the control host's alone, and this is on both.
 */
@UI("/_api-usage")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("APIs externas")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class ApiUsageHome {

    @Menu
    ApiUsageMenu usage;
}
