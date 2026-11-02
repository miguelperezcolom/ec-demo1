package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/** The intranet's configuration, on the control console: every screen needs content:admin. */
public class ContentHubConfigMenu {

    @Menu
    @Label("Secciones")
    SectionsCrud secciones;

    @Menu
    @Label("Menús")
    MenusCrud menus;

    @Menu
    @Label("Menús: vista previa")
    MenuPreviewPage menusVistaPrevia;

    @Menu
    @Label("Audiencias")
    NamedAudiencesCrud audiencias;

    @Menu
    @Label("Slots")
    SlotsCrud slots;

    @Menu
    @Label("Inicio")
    HomesPage inicio;

    @Menu
    @Label("UIs externas")
    UiSourcesCrud uis;

    @Menu
    @Label("Directorio")
    DirectoryListing directorio;

    @Menu
    @Label("Visibilidad")
    ProjectionPage visibilidad;
}
