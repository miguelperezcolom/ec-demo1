package io.mateu.ecdemo1.users.infra.in.ui.pages.people;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

public class PeopleMenu {

    @Menu
    @Label("Empleados")
    PeopleCrud empleados;
}
