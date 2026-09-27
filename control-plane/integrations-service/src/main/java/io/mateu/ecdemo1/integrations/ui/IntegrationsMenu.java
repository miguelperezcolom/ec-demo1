package io.mateu.ecdemo1.integrations.ui;

import io.mateu.ecdemo1.integrations.ui.pages.IntegrationCrud;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

public class IntegrationsMenu {

    /**
     * Named "registry" and labelled, not named "integrations": the entry and its section would
     * otherwise share a name, and /integrations/integrations resolves the section instead of the
     * screen — a page with a link on it, which is not what anyone opening the menu wants.
     */
    @Menu
    @Label("Integrations")
    IntegrationCrud registry;
}
