package io.mateu.ecdemo1.loyalty.infra.in.ui;

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
 * Riu Class on the data plane's console: its members and what their stays earned them. The shells mount
 * it with {@code RemoteMenu("/_loyalty").withLabel("Riu Class")}: that label has to be this one top
 * section's, and the routes hang from the field name — /loyalty/members, /loyalty/accruals.
 */
@UI("/_loyalty")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Riu Class")
@Logo("/images/riu.svg")
@Style(StyleConstants.FULL_WIDTH)
@Service
public class LoyaltyHome {

    @Menu
    @Label("Riu Class")
    LoyaltyMenu loyalty;
}
