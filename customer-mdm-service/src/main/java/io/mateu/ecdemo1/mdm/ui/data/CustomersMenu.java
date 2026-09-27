package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/**
 * The entries of Clientes. Named "search", not "customers": an entry named like its section makes
 * /customers/customers resolve the section instead of the screen.
 */
public class CustomersMenu {

    @Menu
    @Label("Buscar clientes")
    CustomerSearchPage search;

    @Menu
    @Label("Solicitudes de cambio")
    ChangeRequestsPage changes;
}
