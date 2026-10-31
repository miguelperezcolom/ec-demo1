package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

public class ContentHubMenu {

    @Menu
    @Label("Contenidos")
    ItemsCrud contenidos;

    @Menu
    @Label("Medios")
    MediaCrud medios;

    @Menu
    @Label("Secciones")
    SectionsCrud secciones;

    @Menu
    @Label("Audiencias")
    NamedAudiencesCrud audiencias;

    @Menu
    @Label("Ver como")
    ViewAsPage verComo;

    @Menu
    @Label("Slots")
    SlotsCrud slots;

    @Menu
    @Label("Newsletter")
    NewsletterCrud newsletter;

    @Menu
    @Label("Confirmaciones")
    ConfirmationsListing confirmaciones;

    @Menu
    @Label("Pendientes de confirmar")
    PendingConfirmationsListing pendientes;

    @Menu
    @Label("Directorio")
    DirectoryListing directorio;

    @Menu
    @Label("Visibilidad")
    ProjectionPage visibilidad;
}
