package io.mateu.ecdemo1.content.infra.in.ui;

import io.mateu.uidl.annotations.Menu;
import io.mateu.ecdemo1.content.infra.in.ui.pages.content.ContentCrudOrchestrator;
import io.mateu.ecdemo1.content.infra.in.ui.pages.contenttype.ContentTypeCrudOrchestrator;
import io.mateu.ecdemo1.content.infra.in.ui.pages.label.LabelCrudOrchestrator;

public class ContentMenu {

    @Menu
    ContentCrudOrchestrator contents;
    @Menu
    LabelCrudOrchestrator labels;
    @Menu
    ContentTypeCrudOrchestrator contentTypes;

}
