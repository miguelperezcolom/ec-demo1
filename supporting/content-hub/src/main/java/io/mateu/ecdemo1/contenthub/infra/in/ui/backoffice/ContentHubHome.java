package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import org.springframework.stereotype.Service;

/**
 * The intranet's configuration — how it works, not what it publishes: the sections' structure, the named
 * audiences, the slots the applications leave, the employee directory and the visibility projection.
 * Federated into the control console's shells (RemoteMenu → /_content-hub), behind ai-admin at the gateway
 * and content:admin here. The editorial work is the portal's "Editorial" entry, on the data plane.
 */
@UI("/_content-hub")
@Title("")
@PageTitle("Intranet — configuración")
@FavIcon("/images/riu.svg")
@Logo("/images/riu.svg")
@Service
public class ContentHubHome {

    @Menu
    @Label("Intranet")
    @ContentAdmins
    ContentHubConfigMenu intranet;
}
