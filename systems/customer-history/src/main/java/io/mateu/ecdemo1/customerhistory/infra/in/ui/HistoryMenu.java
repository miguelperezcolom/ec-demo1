package io.mateu.ecdemo1.customerhistory.infra.in.ui;

import io.mateu.ecdemo1.customerhistory.infra.in.ui.pages.HistorySearch;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

public class HistoryMenu {

    @Menu
    @Label("Buscar")
    HistorySearch search;
}
