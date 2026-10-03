package io.mateu.ecdemo1.integrations.ui.demo;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

public class DemoMenu {

    /** Named "admin", not "demo": an entry named as its section resolves to the section. */
    @Menu
    @Label("Demo")
    DemoPage admin;
}
