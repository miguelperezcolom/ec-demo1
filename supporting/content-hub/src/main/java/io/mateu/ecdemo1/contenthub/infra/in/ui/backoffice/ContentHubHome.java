package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import org.springframework.stereotype.Service;

/**
 * The intranet's editorial backoffice, federated into the control console's shells (RemoteMenu
 * "content-hub" → /_content-hub). The path here, the gateway's route and the shells' RemoteMenu agree.
 */
@UI("/_content-hub")
@Title("")
@PageTitle("Intranet — backoffice")
@FavIcon("/images/riu.svg")
@Logo("/images/riu.svg")
@Service
public class ContentHubHome {

    @Menu
    @Label("Intranet")
    ContentHubMenu intranet;
}
