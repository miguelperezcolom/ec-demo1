package io.mateu.ecdemo1.audit.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import org.springframework.stereotype.Service;

/** The audit's one screen, federated into the control console (HLA F016: the query lives in the control plane). */
@UI("/_audit")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Audit")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class AuditHome {

    @Menu
    AuditMenu audit;
}
