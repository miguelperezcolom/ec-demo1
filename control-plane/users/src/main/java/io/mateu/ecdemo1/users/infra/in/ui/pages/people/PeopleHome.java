package io.mateu.ecdemo1.users.infra.in.ui.pages.people;

import io.mateu.ecdemo1.users.infra.in.security.PeopleAdmins;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.interfaces.HomeRouteSupplier;
import org.springframework.stereotype.Service;

/**
 * The employees, for human resources: a first-level section of the data plane's shells, only for people:edit.
 * What people may do stays in Usuarios, on the control console. The routes hang from the field: /personas/empleados.
 */
@UI("/_people")
@Title("")
@PageTitle("Personas")
@FavIcon("/images/riu.svg")
@Logo("/images/riu.svg")
@Service
public class PeopleHome implements HomeRouteSupplier {

    @Menu
    @Label("Personas")
    @PeopleAdmins
    PeopleMenu personas;

    @Override
    public String homeRoute() {
        return "/personas/empleados";
    }
}
