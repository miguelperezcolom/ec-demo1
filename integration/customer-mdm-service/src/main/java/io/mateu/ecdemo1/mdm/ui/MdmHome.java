package io.mateu.ecdemo1.mdm.ui;

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
 * The customer MDM's technical screens, federated into the control console (HLA: services bring their
 * own UI). Its business face — Clientes — is a second @UI, for the data plane: see CustomersHome.
 */
@UI("/_mdm")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Customers")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class MdmHome {

    @Menu
    MdmMenu customers;
}
