package io.mateu.ecdemo1.communication.ui.inbox;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

public class InboxMenu {

    /** Named "pending", not "inbox": an entry named as its section resolves to the section. */
    @Menu
    @Label("Pending")
    InboxPage pending;
}
