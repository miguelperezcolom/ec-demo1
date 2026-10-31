package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.interfaces.HomeRouteSupplier;
import org.springframework.stereotype.Service;

/**
 * The portal alone, for the intranet's own host (consoles/intranet-shell → /_portal): what employees
 * read, without the "Editorial" entry the data plane's /_intranet adds for editors. Same menu, so the
 * same routes (/intranet/noticias/{id}…) — the links the portal draws (Ui.itemLink) work in both.
 */
@UI("/_portal")
@Title("")
@PageTitle("Intranet")
@FavIcon("/images/riu.svg")
@Logo("/images/riu.svg")
@Service
public class PortalHome implements HomeRouteSupplier {

    @Menu
    @Label("Intranet")
    IntranetMenu intranet;

    @Override
    public String homeRoute() {
        return "/intranet/inicio";
    }
}
