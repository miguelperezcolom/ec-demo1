package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Publishers;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/**
 * The editorial backoffice, mounted by the portal as its "Editorial" entry (see IntranetHome) for whoever
 * holds a content:* permission: the day-to-day work of writing, publishing and following up. How the
 * intranet is configured — sections, audiences, slots, the directory, the projection — is on the control
 * console (ContentHubHome). Each screen also carries its own restriction, so a typed URL is refused like a
 * hidden entry.
 */
public class ContentHubMenu {

    @Menu
    @Label("Contenidos")
    ItemsCrud contenidos;

    @Menu
    @Label("Medios")
    MediaCrud medios;

    @Menu
    @Label("Contenido de slots")
    @Publishers
    SlotContentCrud slots;

    @Menu
    @Label("Newsletter")
    NewsletterCrud newsletter;

    @Menu
    @Label("Ver como")
    ViewAsPage verComo;

    @Menu
    @Label("Confirmaciones")
    ConfirmationsListing confirmaciones;

    @Menu
    @Label("Pendientes de confirmar")
    PendingConfirmationsListing pendientes;
}
