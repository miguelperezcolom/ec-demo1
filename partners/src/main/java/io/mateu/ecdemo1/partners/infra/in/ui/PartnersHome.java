package io.mateu.ecdemo1.partners.infra.in.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.*;
import org.springframework.stereotype.Service;

@UI("/_partners")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Partners")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class PartnersHome {

    /**
     * "ERP": the master of partners is the ERP's role in this PoC (HLA R38), and that is what it is
     * called on the console. The field name stays, because the routes hang from it.
     */
    @Menu
    @Label("ERP")
    PartnersMenu partners;
}
