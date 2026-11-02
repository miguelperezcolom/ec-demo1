package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.health.ContentHealth;
import io.mateu.ecdemo1.contenthub.application.out.MediaDescriptions;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.domain.health.Finding;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.render.MediaUrls;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Button;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * One finding of «Salud del contenido» and its fix: open what it is about, ask the AI for a text alternative
 * (which the editor reads, changes and applies — never applied by itself), delete an orphan medium
 * (content:admin, confirmed), or mark it as reviewed for a while.
 */
@Service
@Scope("prototype")
@Editors
public class FindingPage implements Identifiable, VisibilitySupplier {

    @Hidden
    String id;
    @Hidden
    String subject;
    @Hidden
    String subjectId;
    @Hidden
    String fix;
    @Hidden
    boolean reviewed;

    @ReadOnly
    @Label("Comprobación")
    String comprobacion;
    @ReadOnly
    @Label("Dónde")
    String donde;
    @ReadOnly
    @Label("Problema")
    @Colspan(2)
    String problema;
    @ReadOnly
    @Label("Cómo se arregla")
    @Colspan(2)
    String arreglo;
    @ReadOnly
    @Label("Visto por primera vez")
    String desde;
    @ReadOnly
    @Label("Estado")
    String estado;

    @Section("Texto alternativo")
    @Label("Texto alternativo (revísalo antes de aplicarlo)")
    @Stereotype(FieldStereotype.textarea)
    @Colspan(2)
    String textoAlternativo;

    @Label("")
    @Colspan(2)
    Callable<Component> vistaPrevia = this::preview;

    @Section("Marcar como revisado")
    @Label("Durante (días)")
    Integer dias = 30;
    @Label("Nota")
    String nota;

    final ContentHealth health;
    final Readers readers;
    final MediaRepository media;
    final MediaDescriptions descriptions;
    final ContentRenderer renderer;

    public FindingPage(ContentHealth health, Readers readers, MediaRepository media, MediaDescriptions descriptions,
                       ContentRenderer renderer) {
        this.health = health;
        this.readers = readers;
        this.media = media;
        this.descriptions = descriptions;
        this.renderer = renderer;
    }

    public FindingPage load(String key, HttpRequest httpRequest) {
        this.id = key;
        var view = health.view(key).orElse(null);
        if (view == null) {
            estado = "Este aviso ya no está: se ha arreglado o la última comprobación ya no lo ve";
            return this;
        }
        var f = view.finding();
        subject = f.subject().name();
        subjectId = f.subjectId();
        fix = f.fix().name();
        reviewed = view.reviewed();
        comprobacion = f.check().label();
        donde = f.subject().label() + ": " + f.subjectTitle() + " (" + f.subjectId() + ")"
                + (f.siteId() == null ? "" : " · sitio «" + f.siteId() + "»");
        problema = f.problem();
        arreglo = howTo(f);
        desde = Ui.when(view.stored().firstSeen());
        estado = view.reviewed() ? "Revisado por " + view.snooze().by() + " hasta el " + Ui.day(view.snooze().until())
                + (view.snooze().note() == null || view.snooze().note().isBlank() ? "" : " — " + view.snooze().note())
                : "Pendiente";
        if (f.fix() == Finding.Fix.SUGGEST_ALT && textoAlternativo == null) {
            textoAlternativo = descriptions.altText(f.subjectId()).orElse(null);
        }
        return this;
    }

    static String howTo(Finding f) {
        return switch (f.fix()) {
            case EDIT_ITEM -> "Abre el contenido («Abrir el contenido»), corrígelo y vuelve a publicarlo.";
            case EDIT_ISSUE -> "Abre el número («Abrir el número») y corrígelo antes de enviarlo.";
            case OPEN_MEDIA -> "Abre el medio en la biblioteca.";
            case SUGGEST_ALT -> "Escribe abajo el texto alternativo, o pide uno a la IA y revísalo; después «Aplicar el texto alternativo».";
            case DELETE_MEDIA -> "Si de verdad no hace falta, un administrador de contenido puede borrarlo («Borrar el medio»). Nunca se borra solo.";
            case CONFIGURE -> "Se corrige en la consola de control → Intranet → " + switch (f.subject()) {
                case SLOT -> "Slots (o en Editorial → Contenido de slots).";
                case HOMES -> "Inicio.";
                case MENU -> "Menús.";
                default -> "su pantalla.";
            };
        };
    }

    Component preview() {
        if (!"MEDIA".equals(subject) || subjectId == null) {
            return Html.block("");
        }
        return Html.block(media.find(subjectId).map(a -> "<div style=\"max-width:480px\">"
                + renderer.medium(a, "/_intranet", MediaUrls.EDITOR) + "</div>").orElse(""));
    }

    @Toolbar
    @Action
    public Object abrirElContenido(HttpRequest httpRequest) {
        return UICommand.navigateTo(VisualEditorPage.formRoute(subjectId));
    }

    @Toolbar
    @Action
    public Object abrirElNumero(HttpRequest httpRequest) {
        return UICommand.navigateTo("/editorial/numeros/" + subjectId);
    }

    @Toolbar
    @Action
    public Object abrirElMedio(HttpRequest httpRequest) {
        return UICommand.navigateTo("/editorial/medios/" + subjectId);
    }

    /** Asks the AI; the text lands in the field, for the editor to read. Nothing is saved. */
    @Button
    @Action
    public Object sugerirTextoAlternativoConIA(HttpRequest httpRequest) {
        try {
            textoAlternativo = health.suggestAlt(readers.of(httpRequest), subjectId, httpRequest.getHeaderValue("Authorization"));
        } catch (RuntimeException e) {
            return Message.error(e.getMessage());
        }
        return List.of(this, Message.success("Propuesta de la IA: revísala, cámbiala si hace falta y pulsa «Aplicar el texto alternativo»."));
    }

    @Button
    @Action
    public Object aplicarElTextoAlternativo(HttpRequest httpRequest) {
        health.applyAlt(readers.of(httpRequest), subjectId, textoAlternativo);
        return List.of(load(id, httpRequest), Message.success("Texto alternativo guardado: ya se usa en el portal"),
                UICommand.dispatchEvent(HealthListing.CHANGED));
    }

    @Toolbar
    @Action
    public Object marcarComoRevisado(HttpRequest httpRequest) {
        health.snooze(readers.of(httpRequest), id, dias == null ? 30 : dias, nota);
        return List.of(load(id, httpRequest), Message.success("Marcado como revisado: no cuenta hasta dentro de "
                + (dias == null ? 30 : dias) + " días, si sigue ahí"), UICommand.dispatchEvent(HealthListing.CHANGED));
    }

    @Toolbar
    @Action
    public Object volverAPendiente(HttpRequest httpRequest) {
        health.unsnooze(readers.of(httpRequest), id);
        return List.of(load(id, httpRequest), Message.success("Vuelve a estar pendiente"),
                UICommand.dispatchEvent(HealthListing.CHANGED));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "¿Borrar el medio?",
            confirmationMessage = "Se borra de la biblioteca para siempre. Antes se comprueba otra vez que nada lo usa.")
    public Object borrarElMedio(HttpRequest httpRequest) {
        health.deleteOrphan(readers.of(httpRequest), subjectId);
        return List.of(Message.success("Medio borrado"), UICommand.dispatchEvent(HealthListing.CHANGED),
                UICommand.navigateTo("/editorial/salud"));
    }

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        var canEdit = reader.may(EditorialPermission.EDIT);
        var isMedia = "MEDIA".equals(subject);
        return switch (fieldName) {
            case "abrirElContenido" -> !"ITEM".equals(subject);
            case "abrirElMedio" -> !isMedia;
            case "abrirElNumero" -> !"ISSUE".equals(subject);
            case "textoAlternativo", "aplicarElTextoAlternativo" -> !"SUGGEST_ALT".equals(fix) || !canEdit;
            case "sugerirTextoAlternativoConIA" -> !"SUGGEST_ALT".equals(fix) || !canEdit || !health.altTextAvailable();
            case "vistaPrevia" -> !isMedia;
            case "borrarElMedio" -> !"DELETE_MEDIA".equals(fix) || !reader.may(EditorialPermission.ADMIN);
            case "marcarComoRevisado", "dias", "nota" -> reviewed || !canEdit || subject == null;
            case "volverAPendiente" -> !reviewed || !canEdit;
            default -> false;
        };
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Aviso de salud del contenido";
    }
}
