package io.mateu.ecdemo1.registration.infra.in.ui;

import io.mateu.ecdemo1.registration.infra.in.ui.pages.EvaluateRules;
import io.mateu.ecdemo1.registration.infra.in.ui.pages.RuleCrud;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

public class RulesMenu {

    @Menu
    @Label("Reglas de registro")
    RuleCrud reglas;

    @Menu
    @Label("Probar con un huésped")
    EvaluateRules probar;
}
