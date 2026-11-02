package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.PreviewLinks;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLinkStatus;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.DetailContentSupplier;
import io.mateu.uidl.data.FieldDataType;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.HorizontalLayout;
import io.mateu.uidl.data.VerticalLayout;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An item's reviewer preview links ({@code /editorial/vistaPrevia/{itemId}}, «Enlace de vista previa» in its
 * form): make one — how many days, and optionally as which profile it shows the draft —, see each with its
 * state and views, revoke one. The link opens a page of the portal that anyone logged in can see.
 *
 * <p>The listing opens this as its record's page, which Mateu draws read-only from the fields: the inputs
 * are fluent {@link FormField}s in {@link #detailContent} instead (as HistoryPage and ApprovalPage do),
 * bound by id to the hidden fields the actions read.
 */
@Service
@Scope("prototype")
@Editors
public class PreviewLinksPage implements Identifiable, DetailContentSupplier {

    @Hidden
    String id;
    @Hidden
    String title;

    @Hidden
    Integer dias = (int) PreviewLink.DEFAULT_VALIDITY.toDays();
    @Hidden
    String verComo;
    @Hidden
    String enlaceARevocar;

    final PreviewLinks previewLinks;
    final ItemRepository items;
    final AudienceCommands audiences;
    final Readers readers;

    public PreviewLinksPage(PreviewLinks previewLinks, ItemRepository items, AudienceCommands audiences, Readers readers) {
        this.previewLinks = previewLinks;
        this.items = items;
        this.audiences = audiences;
        this.readers = readers;
    }

    public PreviewLinksPage load(String itemId) {
        var item = items.find(itemId).orElseThrow(() -> new IllegalArgumentException("No existe el contenido " + itemId));
        this.id = itemId;
        this.title = item.draft().in(item.defaultLanguage(), item.defaultLanguage()).title();
        return this;
    }

    @Override
    public Component detailContent(HttpRequest httpRequest) {
        var profiles = audiences.allProfiles().values().stream().map(p -> new Option(p.hash(),
                p.attributes().canonical().replace(";", " · ") + " — " + p.employees() + " empleado(s)")).toList();
        var active = id == null ? List.<Option>of() : previewLinks.ofItem(id).stream()
                .filter(l -> previewLinks.status(l) == PreviewLinkStatus.ACTIVE)
                .map(l -> new Option(l.id(), "Creado el " + Ui.when(l.createdAt()) + " por " + l.createdBy()
                        + " · " + l.viewCount() + " vista(s)")).toList();
        var content = new java.util.ArrayList<Component>();
        content.add(Html.block("<h3 style=\"margin:0\">Nuevo enlace</h3>"));
        content.add(HorizontalLayout.builder().spacing(true).style("flex-wrap: wrap; align-items: flex-end;").content(List.of(
                FormField.builder().id("dias").label("Válido durante (días)").dataType(FieldDataType.integer)
                        .initialValue(dias).description("De 1 a 30. Deja de valer antes si lo revocas o si se publica el contenido.")
                        .style("min-width: 12rem;").build(),
                FormField.builder().id("verComo").label("Enseñarlo como (opcional)").dataType(FieldDataType.string)
                        .stereotype(FieldStereotype.select).options(profiles).initialValue(verComo)
                        .description("Un perfil del directorio: el revisor ve el borrador como lo verá ese perfil. Vacío: como él mismo.")
                        .style("min-width: 22rem;").build())).build());
        content.add(Html.block("<h3 style=\"margin:.5rem 0 0\">Enlaces de este contenido</h3>" + list(previewLinks, id)));
        content.add(FormField.builder().id("enlaceARevocar").label("Enlace a revocar").dataType(FieldDataType.string)
                .stereotype(FieldStereotype.select).options(active).initialValue(enlaceARevocar).style("min-width: 22rem;").build());
        return VerticalLayout.builder().fullWidth(true).spacing(true).content(content).build();
    }

    /** An item's links as markup: the backoffice's list, here and in the item's form. */
    static String list(PreviewLinks previewLinks, String itemId) {
        var all = itemId == null ? List.<PreviewLink>of() : previewLinks.ofItem(itemId);
        if (all.isEmpty()) {
            return "<p style=\"opacity:.7\">Sin enlaces de vista previa. Crea uno con «Crear enlace de vista previa».</p>";
        }
        return all.stream().map(l -> row(previewLinks, l)).collect(Collectors.joining());
    }

    static String row(PreviewLinks previewLinks, PreviewLink l) {
        var status = previewLinks.status(l);
        var color = switch (status) {
            case ACTIVE -> "#2f8f5f";
            case EXPIRED -> "#8a6d1d";
            case REVOKED -> "#a33";
            case PUBLISHED -> "#555";
        };
        var url = previewLinks.url(l);
        var latest = l.views().reversed().stream().limit(5)
                .map(v -> Ui.esc(v.userId()) + " (" + Ui.when(v.at()) + ")").collect(Collectors.joining(", "));
        return "<div style=\"border:1px solid rgba(128,128,128,.25);border-radius:12px;padding:.6rem 1rem;margin:0 0 .6rem\">"
                + Ui.chip(status.label(), color)
                + (status == PreviewLinkStatus.ACTIVE
                ? "<a href=\"" + Ui.esc(url) + "\" target=\"_blank\" rel=\"noopener\"><code>" + Ui.esc(url) + "</code></a>"
                : "<code style=\"opacity:.6\">" + Ui.esc(url) + "</code>")
                + "<div style=\"font-size:.85rem;opacity:.85;margin-top:.3rem\">Creado por " + Ui.esc(l.createdBy())
                + " el " + Ui.when(l.createdAt()) + " · caduca el " + Ui.when(l.expiresAt())
                + (l.revokedAt() == null ? "" : " · revocado por " + Ui.esc(l.revokedBy()) + " el " + Ui.when(l.revokedAt()))
                + (l.viewAs() == null ? "" : " · se ve como «" + Ui.esc(l.viewAsLabel()) + "»")
                + "</div><div style=\"font-size:.85rem;margin-top:.2rem\"><b>" + l.viewCount() + "</b> vista(s), "
                + l.uniqueViewers() + " persona(s)" + (latest.isEmpty() ? "" : " · últimas: " + latest) + "</div></div>";
    }

    @Toolbar
    @Action
    @Label("Crear enlace de vista previa")
    public Object crearEnlace(HttpRequest httpRequest) {
        String label = null;
        if (verComo != null && !verComo.isBlank()) {
            var profile = audiences.allProfiles().get(verComo);
            if (profile == null) {
                return List.of(this, Message.warning("Ese perfil ya no está en el directorio"));
            }
            label = profile.attributes().canonical().replace(";", " · ");
        }
        var link = previewLinks.create(readers.real(httpRequest::getHeaderValue), id, dias == null ? 0 : dias, verComo, label);
        verComo = null;
        return List.of(this, Message.success("Enlace creado, válido hasta el " + Ui.when(link.expiresAt())
                + ". Cópialo y compártelo: " + previewLinks.url(link)));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "¿Revocar el enlace?",
            confirmationMessage = "Quien lo abra verá que ya no es válido. No se puede deshacer: crea otro si hace falta.")
    @Label("Revocar enlace")
    public Object revocarEnlace(HttpRequest httpRequest) {
        if (enlaceARevocar == null || enlaceARevocar.isBlank()) {
            return List.of(this, Message.warning("Elige en «Enlace a revocar» cuál"));
        }
        previewLinks.revoke(readers.real(httpRequest::getHeaderValue), enlaceARevocar);
        enlaceARevocar = null;
        return List.of(this, Message.success("Enlace revocado"));
    }

    @Toolbar
    @Action
    public Object volverAlFormulario(HttpRequest httpRequest) {
        return UICommand.navigateTo(VisualEditorPage.formRoute(id));
    }

    public static String route(String itemId) {
        return "/editorial/vistaPrevia/" + itemId;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Vista previa · " + (title == null ? id : title);
    }
}
