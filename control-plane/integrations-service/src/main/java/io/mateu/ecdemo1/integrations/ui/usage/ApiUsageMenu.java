package io.mateu.ecdemo1.integrations.ui.usage;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

public class ApiUsageMenu {

    /** Named "apis", not "usage": an entry named as its section resolves to the section. */
    @Menu
    @Label("APIs externas")
    ApiUsagePage apis;
}
