package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/**
 * The editorial backoffice, mounted by the portal as its "Editorial" entry (see IntranetHome) for whoever
 * holds a content:* permission. The administration entries need content:admin on top; each screen also
 * carries its own restriction, so a typed URL is refused like a hidden entry.
 */
public class ContentHubMenu {

    @Menu
    @Label("Contenidos")
    ItemsCrud contenidos;

    @Menu
    @Label("Medios")
    MediaCrud medios;

    @Menu
    @Label("Secciones")
    @ContentAdmins
    SectionsCrud secciones;

    @Menu
    @Label("Audiencias")
    @ContentAdmins
    NamedAudiencesCrud audiencias;

    @Menu
    @Label("Ver como")
    ViewAsPage verComo;

    @Menu
    @Label("Slots")
    @ContentAdmins
    SlotsCrud slots;

    @Menu
    @Label("Newsletter")
    @ContentAdmins
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
    @ContentAdmins
    ProjectionPage visibilidad;
}
