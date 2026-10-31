package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/** The intranet's configuration, on the control console: every screen needs content:admin. */
public class ContentHubConfigMenu {

    @Menu
    @Label("Secciones")
    SectionsCrud secciones;

    @Menu
    @Label("Audiencias")
    NamedAudiencesCrud audiencias;

    @Menu
    @Label("Slots")
    SlotsCrud slots;

    @Menu
    @Label("Directorio")
    DirectoryListing directorio;

    @Menu
    @Label("Visibilidad")
    ProjectionPage visibilidad;
}
