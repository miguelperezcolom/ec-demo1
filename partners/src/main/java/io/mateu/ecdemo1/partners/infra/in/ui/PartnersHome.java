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

    @Menu
    PartnersMenu partners;
}
