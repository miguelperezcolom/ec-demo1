package io.mateu.ecdemo1.mapping.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.*;
import org.springframework.stereotype.Service;

/** The mapping's screens, federated into the control console (HLA: services bring their own UI). */
@UI("/_mapping")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Mapping")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@Service
public class MappingHome {

    @Menu
    MappingMenu mapping;
}
