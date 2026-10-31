package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/** The portal's entries: the routes hang from the field names (/intranet/noticias/{id}…). */
public class IntranetMenu {

    @Menu
    @Label("Inicio")
    HomePage inicio;

    @Menu
    @Label("Noticias")
    NewsListing noticias;

    @Menu
    @Label("Avisos")
    NoticesListing avisos;

    @Menu
    @Label("Documentos")
    DocumentsListing documentos;

    @Menu
    @Label("Secciones")
    SectionsListing secciones;

    @Menu
    @Label("Buscar")
    SearchListing buscar;

    @Menu
    @Label("Mis favoritos")
    FavoritesListing favoritos;

    @Menu
    @Label("Pendientes")
    PendingListing pendientes;
}
