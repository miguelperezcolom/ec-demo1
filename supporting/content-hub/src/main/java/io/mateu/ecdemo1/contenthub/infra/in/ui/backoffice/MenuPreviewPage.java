package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.menu.MenuContext;
import io.mateu.ecdemo1.contenthub.application.menu.MenuLink;
import io.mateu.ecdemo1.contenthub.application.menu.ShownEntry;
import io.mateu.ecdemo1.contenthub.application.menu.SiteMenus;
import io.mateu.ecdemo1.contenthub.application.menu.SiteRoutes;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * "Ver como" for a menu: the site's menu exactly as an employee (or a profile of the directory) gets it —
 * the same drawing the portal does, not a simulation. Also the way back to the menu a site starts with.
 */
@Service
@Scope("prototype")
@Title("Menús — vista previa")
@ContentAdmins
public class MenuPreviewPage implements OptionsSupplier {

    @Section("Menú")
    @Label("Menú del sitio")
    @Stereotype(FieldStereotype.select)
    String site = SiteMenu.INTRANET;
    @Label("Como un empleado")
    @Stereotype(FieldStereotype.select)
    String employee;
    @Label("…o como un perfil del directorio")
    @Stereotype(FieldStereotype.select)
    String profile;

    @Section("Así lo vería")
    @Label("")
    @Colspan(2)
    Callable<Component> preview = this::preview;

    final SiteMenus menus;
    final SiteRoutes routes;
    final EmployeeDirectory directory;
    final AudienceCommands audiences;
    final Readers readers;

    public MenuPreviewPage(SiteMenus menus, SiteRoutes routes, EmployeeDirectory directory, AudienceCommands audiences,
                           Readers readers) {
        this.menus = menus;
        this.routes = routes;
        this.directory = directory;
        this.audiences = audiences;
        this.readers = readers;
    }

    EmployeeAttributes chosen() {
        if (employee != null && !employee.isBlank()) {
            return directory.find(employee).map(e -> e.attributes()).orElse(null);
        }
        if (profile != null && !profile.isBlank()) {
            var p = audiences.allProfiles().get(profile);
            return p == null ? null : p.attributes();
        }
        return null;
    }

    String siteId() {
        return site == null || site.isBlank() ? SiteMenu.INTRANET : site;
    }

    Component preview() {
        var attrs = chosen();
        if (attrs == null) {
            return Html.block("<p style=\"opacity:.7\">Elige un empleado o un perfil y pulsa «Previsualizar».</p>");
        }
        var reader = new Reader("preview", "Vista previa", attrs, Set.of(), null);
        var shown = menus.shown(new MenuContext(siteId(), reader, Set.of()));
        return Html.block("<p><b>Perfil:</b> " + Ui.esc(attrs.canonical().replace(";", " · ")) + " · idioma "
                + attrs.language() + "</p>"
                + Ui.card(shown.isEmpty() ? "<i>No vería ninguna entrada</i>" : html(siteId(), shown, routes))
                + "<p style=\"opacity:.7;font-size:.85rem\">Las entradas a otras interfaces que piden permisos no se "
                + "muestran aquí (la vista previa no tiene los permisos del empleado).</p>");
    }

    /** A shown menu as a nested list, each entry with where it goes. */
    public static String html(String siteId, List<ShownEntry> entries, SiteRoutes routes) {
        var out = new StringBuilder("<ul style=\"margin:.25rem 0;padding-left:1.25rem\">");
        for (var e : entries) {
            out.append("<li>").append(e.isSubmenu() ? "<b>" + Ui.esc(e.label()) + "</b>" : Ui.esc(e.label()));
            if (!e.isSubmenu()) {
                out.append(" <span style=\"opacity:.6;font-size:.85rem\">→ ").append(Ui.esc(where(siteId, e, routes)))
                        .append("</span>");
            }
            if (!e.children().isEmpty()) {
                out.append(html(siteId, e.children(), routes));
            }
            out.append("</li>");
        }
        return out.append("</ul>").toString();
    }

    static String where(String siteId, ShownEntry e, SiteRoutes routes) {
        return switch (e.link()) {
            case MenuLink.Function f -> routes.function(siteId, f.key());
            case MenuLink.Route r -> r.path();
            case MenuLink.External x -> x.url() + " (pestaña nueva)";
            case MenuLink.Remote r -> r.source().name() + " " + r.source().basePath() + r.route();
        };
    }

    @Toolbar
    @Action
    public Object previsualizar(HttpRequest httpRequest) {
        return chosen() == null ? Message.warning("Elige un empleado o un perfil") : this;
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "¿Volver al menú inicial?",
            confirmationMessage = "Se pierden las entradas y el orden configurados para este sitio. En la intranet, vuelve el menú de siempre.")
    @Label("Volver al menú inicial")
    public Object volverAlMenuInicial(HttpRequest httpRequest) {
        menus.reset(readers.of(httpRequest), siteId());
        return Message.success("El menú de «" + siteId() + "» vuelve a ser el inicial");
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return List.of("site", "employee", "profile").contains(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "site" -> menus.sites().stream().map(s -> new Option(s, s)).toList();
            case "employee" -> directory.all().stream().map(e -> new Option(e.userId(), e.name() + " (" + e.userId() + ")")).toList();
            default -> audiences.allProfiles().values().stream().map(p -> new Option(p.hash(),
                    p.attributes().canonical().replace(";", " · ") + " — " + p.employees() + " empleado(s)")).toList();
        };
    }
}
