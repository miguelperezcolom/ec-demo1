package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
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
 * The editorial backoffice, a first-level section of the data plane's shells of its own, next to the
 * intranet: {@code RemoteMenu("/_editorial").withLabel("Editorial")}. That label has to be this one top
 * section's, and the routes hang from the field name: /editorial/contenidos and so on, shown and routed
 * only to whoever holds a content:* permission ({@link Editors}).
 */
@UI("/_editorial")
@Title("")
@PageTitle("Editorial")
@FavIcon("/images/riu.svg")
@Logo("/images/riu.svg")
@Service
public class EditorialHome implements HomeRouteSupplier {

    @Menu
    @Label("Editorial")
    @Editors
    ContentHubMenu editorial;

    @Override
    public String homeRoute() {
        return "/editorial/contenidos";
    }
}
