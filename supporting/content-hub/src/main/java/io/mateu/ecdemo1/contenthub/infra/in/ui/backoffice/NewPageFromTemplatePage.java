package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.usecases.Templates;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.template.PageTemplate;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
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
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * «Nueva página desde plantilla»: pick the site (the intranet, or a newsletter), a template — the global ones
 * and that site's —, give the page its title (and, if you like, one of the site's sections),
 * and a PAGE draft is made with the template's blocks and default audience — then opened in its form or on
 * the page itself (the visual editor). For any editor (content:edit).
 */
@Service
@Scope("prototype")
@Title("Nueva página desde plantilla")
@Editors
public class NewPageFromTemplatePage implements OptionsSupplier {

    /** Its menu entry's (ContentHubMenu.nuevaPagina): Mateu routes a menu entry by its field's name. */
    public static final String ROUTE = "/editorial/nuevaPagina";

    @Section("Página nueva")
    @Label("Sitio")
    @Stereotype(FieldStereotype.select)
    String sitio = io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET;
    @Label("Plantilla (las globales y las del sitio)")
    @Stereotype(FieldStereotype.select)
    String plantilla;
    @Label("Título de la página")
    String titulo;
    @Label("Sección (opcional)")
    @Stereotype(FieldStereotype.select)
    String seccion;

    @Section("Plantillas")
    @Label("")
    @Colspan(2)
    Callable<Component> plantillas = this::catalogue;

    final Templates templates;
    final NamedAudiences named;
    final Readers readers;
    final io.mateu.ecdemo1.contenthub.application.out.SiteRepository sites;

    public NewPageFromTemplatePage(Templates templates, NamedAudiences named, Readers readers,
                                   io.mateu.ecdemo1.contenthub.application.out.SiteRepository sites) {
        this.sites = sites;
        this.templates = templates;
        this.named = named;
        this.readers = readers;
    }

    /** Every template, what it is for and what it brings; the chosen one, marked. */
    Component catalogue() {
        var all = templates.availableIn(sitio);
        if (all.isEmpty()) {
            return Html.block("<p style=\"opacity:.7\">Todavía no hay plantillas. Un publicador las crea en "
                    + "<i>Plantillas</i>, o guarda una página como plantilla desde su formulario.</p>");
        }
        var labels = named.labels();
        return Html.block("<div style=\"display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:.75rem\">"
                + all.stream().map(t -> card(t, labels)).collect(Collectors.joining()) + "</div>");
    }

    String card(PageTemplate t, java.util.function.Function<String, String> labels) {
        var chosen = t.id().equals(plantilla);
        return "<div style=\"border:" + (chosen ? "2px solid #2f8f8f" : "1px solid rgba(128,128,128,.3)")
                + ";border-radius:12px;padding:.75rem 1rem\">"
                + "<b>" + Ui.esc(t.name()) + "</b>" + (chosen ? " ✓" : "")
                + (t.description() == null || t.description().isBlank() ? "" : "<p style=\"margin:.35rem 0;opacity:.8\">" + Ui.esc(t.description()) + "</p>")
                + "<p style=\"margin:.35rem 0;font-size:.85rem\">" + t.blocks().stream().map(Block::type).map(b -> Ui.esc(b.label()))
                .collect(Collectors.joining(" · ")) + "</p>"
                + "<p style=\"margin:0;font-size:.8rem;opacity:.7\">" + (t.siteId() == null ? "Global" : "Sitio " + Ui.esc(t.siteId()))
                + " · audiencia: "
                + Ui.esc(t.defaultAudience() == null ? "todos" : t.defaultAudience().describe(labels)) + "</p></div>";
    }

    /** The sites to make a page in, or to keep a template for: the intranet first, then the newsletters. */
    static List<Option> siteOptions(io.mateu.ecdemo1.contenthub.application.out.SiteRepository sites) {
        var out = new java.util.ArrayList<Option>();
        out.add(new Option(io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET, "Intranet"));
        sites.findAll().stream().filter(s -> !io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET.equals(s.id()))
                .forEach(s -> out.add(new Option(s.id(), s.name())));
        return out;
    }

    /** Redraws the catalogue with the chosen template marked (and the chosen site's templates). */
    @Toolbar
    @Action
    @Label("Ver la plantilla elegida")
    public Object verPlantilla(HttpRequest httpRequest) {
        return this;
    }

    @Toolbar
    @Action
    @Label("Crear y abrir el formulario")
    public Object crearYAbrirFormulario(HttpRequest httpRequest) {
        var missing = missing();
        if (missing != null) {
            return Message.warning(missing);
        }
        var id = templates.newPage(readers.of(httpRequest), plantilla, titulo, seccion, sitio);
        return UICommand.navigateTo(VisualEditorPage.formRoute(id));
    }

    @Toolbar
    @Action
    @Label("Crear y editar sobre la página")
    public Object crearYEditarSobreLaPagina(HttpRequest httpRequest) {
        var missing = missing();
        if (missing != null) {
            return Message.warning(missing);
        }
        var id = templates.newPage(readers.of(httpRequest), plantilla, titulo, seccion, sitio);
        return UICommand.navigateTo(VisualEditorPage.route(id));
    }

    String missing() {
        if (plantilla == null || plantilla.isBlank()) {
            return "Elige una plantilla";
        }
        if (titulo == null || titulo.isBlank()) {
            return "Dale un título a la página";
        }
        return null;
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "plantilla".equals(fieldName) || "seccion".equals(fieldName) || "sitio".equals(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "plantilla" -> templates.availableIn(sitio).stream().map(t -> new Option(t.id(), t.name())).toList();
            case "sitio" -> siteOptions(sites);
            default -> Catalog.get().sections(io.mateu.ecdemo1.contenthub.domain.site.Site.of(sitio));
        };
    }
}
