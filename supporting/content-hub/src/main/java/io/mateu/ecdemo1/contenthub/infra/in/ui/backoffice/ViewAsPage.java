package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * "Ver como": the portal exactly as a profile would see it — menu, listings and search through the same
 * visibility function, not a simulation. Pick an employee, a profile, or a named audience (its most
 * numerous profile); the preview is drawn here, and «Ver la intranet así» makes the portal itself read
 * as that profile for you, with a banner, until «Salir».
 */
@Service
@Scope("prototype")
@Title("Ver como")
@Editors
public class ViewAsPage implements OptionsSupplier {

    @Section("Perfil")
    @Label("Un empleado")
    @Stereotype(FieldStereotype.select)
    String employee;
    @Label("…o un perfil del directorio")
    @Stereotype(FieldStereotype.select)
    String profile;
    @Label("…o una audiencia con nombre (su perfil más numeroso)")
    @Stereotype(FieldStereotype.select)
    String namedAudience;

    @Section("Así lo vería")
    @Label("")
    @Colspan(2)
    Callable<Component> preview = this::preview;

    final EmployeeDirectory directory;
    final AudienceCommands audiences;
    final NamedAudienceRepository namedAudiences;
    final NavigationRepository navigation;
    final Visibility visibility;
    final Readers readers;

    @Value("${content-hub.portal-url:http://console.localhost:8800/intranet}")
    String portalUrl;

    public ViewAsPage(EmployeeDirectory directory, AudienceCommands audiences, NamedAudienceRepository namedAudiences,
                      NavigationRepository navigation, Visibility visibility, Readers readers) {
        this.directory = directory;
        this.audiences = audiences;
        this.namedAudiences = namedAudiences;
        this.navigation = navigation;
        this.visibility = visibility;
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
        if (namedAudience != null && !namedAudience.isBlank()) {
            return namedAudiences.find(namedAudience)
                    .flatMap(a -> audiences.profilesIn(a.expression()).stream().findFirst())
                    .map(AudienceCommands.Profile::attributes).orElse(null);
        }
        return null;
    }

    Component preview() {
        var attrs = chosen();
        if (attrs == null) {
            return Html.block("<p style=\"opacity:.7\">Elige un empleado, un perfil o una audiencia y pulsa «Previsualizar».</p>");
        }
        var nav = navigation.get();
        var shown = nav.shown(visibility.sectionsWithVisibleContent(attrs));
        var menu = nav.sections().stream().filter(s -> shown.contains(s.id()))
                .map(s -> "  ".repeat(nav.ancestors(s.id()).size()) + "• " + Ui.esc(s.name(attrs.language())))
                .reduce("", (a, b) -> a + b + "<br/>");
        var items = visibility.visibles(attrs, ContentQuery.latest(30));
        return Html.block("<p><b>Perfil:</b> " + Ui.esc(attrs.canonical().replace(";", " · ")) + " · idioma "
                + attrs.language() + " · hash <code>" + attrs.hash() + "</code></p>"
                + "<div style=\"display:grid;grid-template-columns:1fr 2fr;gap:1rem\">"
                + Ui.card("<h3 style=\"margin-top:0\">Menú</h3>" + (menu.isEmpty() ? "<i>sin secciones</i>" : menu))
                + Ui.card("<h3 style=\"margin-top:0\">Contenido visible (" + items.total() + ")</h3>"
                + PageRenderer.list(items.content())) + "</div>");
    }

    @Toolbar
    @Action
    public Object previsualizar(HttpRequest httpRequest) {
        return chosen() == null ? Message.warning("Elige un perfil") : Message.success("Vista previa actualizada");
    }

    @Toolbar
    @Action
    public Object verLaIntranetAsi(HttpRequest httpRequest) {
        var attrs = chosen();
        if (attrs == null) {
            return Message.warning("Elige un perfil");
        }
        var reader = readers.real(httpRequest::getHeaderValue);
        visibility.isVisible(attrs, "-"); // makes sure the profile is materialised
        readers.viewAs(reader.userId(), attrs.hash());
        return Message.success("Abre la intranet (" + portalUrl + "): la verás como este perfil hasta que pulses «Salir de ver como».");
    }

    @Toolbar
    @Action
    public Object salirDeVerComo(HttpRequest httpRequest) {
        readers.viewAs(readers.real(httpRequest::getHeaderValue).userId(), null);
        return Message.success("La intranet vuelve a mostrarse con tu propio perfil");
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return List.of("employee", "profile", "namedAudience").contains(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "employee" -> directory.all().stream().map(e -> new Option(e.userId(), e.name() + " (" + e.userId() + ")")).toList();
            case "profile" -> audiences.allProfiles().values().stream().map(p -> new Option(p.hash(),
                    p.attributes().canonical().replace(";", " · ") + " — " + p.employees() + " empleado(s)")).toList();
            default -> Catalog.get().namedAudiences();
        };
    }
}
