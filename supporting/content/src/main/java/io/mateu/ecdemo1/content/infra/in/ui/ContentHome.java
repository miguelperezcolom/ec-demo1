package io.mateu.ecdemo1.content.infra.in.ui;

import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.UI;

@UI("/_content")
public class ContentHome {

    @Menu
    ContentMenu content;

}
