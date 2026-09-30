package io.mateu.ecdemo1.notices.infra.in.ui;

import io.mateu.ecdemo1.notices.infra.in.ui.pages.NoticeCrud;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

public class NoticesMenu {

    @Menu
    @Label("Avisos de recepción")
    NoticeCrud recepcion;
}
