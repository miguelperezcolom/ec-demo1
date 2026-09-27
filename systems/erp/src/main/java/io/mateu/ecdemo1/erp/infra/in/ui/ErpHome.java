package io.mateu.ecdemo1.erp.infra.in.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.*;
import org.springframework.stereotype.Service;

@UI("/_erp")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("ERP")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class ErpHome {

    /**
     * "ERP": the master of partners is the ERP's role in this PoC (HLA R38), and that is what it is
     * called on the console. The field name stays, because the routes hang from it.
     */
    @Menu
    @Label("ERP")
    PartnersMenu partners;
}
