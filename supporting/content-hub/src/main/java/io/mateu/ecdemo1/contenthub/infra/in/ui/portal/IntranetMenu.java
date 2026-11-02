package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.uidl.interfaces.Actionable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.MenuSupplier;

import java.util.List;

/**
 * The portal's entries. What the menu shows — which entries, in which order, under which submenus, with
 * which labels, to whom — is data: the intranet's menu, configured on the control console
 * (Configuración → Menús) and drawn per reader by {@link PortalMenu}.
 *
 * <p>The routes are not data: they hang from these fields' names (/intranet/noticias/{id}…), as they
 * always have. Each one is a portal function ({@code PortalFunction}'s key), and every one stays routed
 * whether the menu shows it or not — deep links, reloads and links from the content keep working.
 */
public class IntranetMenu implements MenuSupplier {

    HomePage inicio;
    NewsListing noticias;
    NoticesListing avisos;
    DocumentsListing documentos;
    SectionsListing secciones;
    PeopleListing personas;
    OrgChartPage organigrama;
    SearchPage buscar;
    DriveFilesListing documentosDrive;
    FavoritesListing favoritos;
    PendingListing pendientes;
    NewRequestPage nuevaSolicitud;
    MyRequestsListing solicitudes;
    ApprovalsListing porAprobar;
    SurveysListing encuestas;
    NewslettersListing newsletters;
    PreferencesPage preferencias;

    @Override
    public List<Actionable> menu(HttpRequest httpRequest) {
        return PortalMenu.get().actionables(SiteMenu.INTRANET, IntranetMenu.class, httpRequest);
    }
}
