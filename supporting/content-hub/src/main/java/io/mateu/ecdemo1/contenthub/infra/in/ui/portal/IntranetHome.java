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
 * The intranet portal, federated into the data plane's shells (RemoteMenu "intranet" → /_intranet).
 * The path here, the gateway's route and the shells' RemoteMenu have to agree.
 */
@UI("/_intranet")
@Title("")
@PageTitle("Intranet")
@FavIcon("/images/riu.svg")
@Logo("/images/riu.svg")
@Service
public class IntranetHome implements HomeRouteSupplier {

    @Menu
    @Label("Intranet")
    IntranetMenu intranet;

    @Override
    public String homeRoute() {
        return "/intranet/inicio";
    }
}
