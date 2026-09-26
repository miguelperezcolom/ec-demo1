package io.mateu.ecdemo1.mapping.ui;

import io.mateu.ecdemo1.mapping.ui.pages.CausesPage;
import io.mateu.ecdemo1.mapping.ui.pages.DictionaryCrud;
import io.mateu.ecdemo1.mapping.ui.pages.PartnerProfilesPage;
import io.mateu.uidl.annotations.Menu;

public class MappingMenu {

    /** First, because it is the operator's unit of work: what is blocking, and how much behind it. */
    @Menu
    CausesPage causes;

    @Menu
    DictionaryCrud dictionary;

    @Menu
    PartnerProfilesPage partnerProfiles;
}
