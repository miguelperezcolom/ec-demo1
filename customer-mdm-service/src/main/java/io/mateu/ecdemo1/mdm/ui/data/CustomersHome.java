package io.mateu.ecdemo1.mdm.ui.data;

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
 * The customer master's business face, federated into the DATA plane's shells: finding a customer
 * and seeing it whole — its data, where it is known, its reservations in every system and the
 * changes asked for it. Read-only: changes are asked for at a hotel's reception and Salesforce
 * decides them. The technical screens (golden records, consolidations) stay on the control plane,
 * under {@code /_mdm}.
 *
 * <p>The path here, the gateway's route for the data plane's hosts and the shells' RemoteMenu have
 * to agree.
 */
@UI("/_customers")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Clientes")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class CustomersHome {

    /** "Clientes": the label the shells' RemoteMenu repeats. The routes hang from the field name. */
    @Menu
    @Label("Clientes")
    CustomersMenu customers;
}
