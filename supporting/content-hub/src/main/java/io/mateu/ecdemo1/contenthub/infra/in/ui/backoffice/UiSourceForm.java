package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.UiSourceCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSourceKind;
import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An external UI of the registry: where it lives (a gateway path; an allow-listed https URL for an
 * iframe), and who may use it — the audience builder of everything else, and the scopes it requires.
 * Editors then choose it for a remote page or a «Componente remoto» block; the menus, for a REMOTE entry.
 */
@Service
@Scope("prototype")
@ContentAdmins
public class UiSourceForm extends AudienceForm implements SimpleCrud.CrudForm, VisibilitySupplier {

    @Section("UI externa")
    @NotEmpty
    @EditableOnlyWhenCreating
    @Label("Id")
    @Help("Minúsculas, cifras y guiones: riu-class. Es lo que guardan las páginas, los bloques y los menús que la usan.")
    String id;
    @NotEmpty
    @Label("Nombre")
    String name;
    @NotNull
    @Label("Tipo")
    UiSourceKind kind = UiSourceKind.MATEU_REMOTE;
    @NotEmpty
    @Label("Dónde está")
    @Help("Una UI de Mateu: su prefijo en el gateway (/_loyalty). Un web component: la ruta de su script en el "
            + "gateway (/_inbox/push/push.js). Un iframe: una URL https de un origen permitido. "
            + "El prefijo tiene que estar enrutado en el host donde se lee la intranet.")
    String basePath;
    @Label("Etiqueta del web component")
    @Help("El nombre del elemento que define el script: ec-push-toggle.")
    String tagName;
    @Label("Permisos que exige (separados por espacios)")
    @Help("El lector necesita todos (X-User-Scopes del gateway). Vacío: ninguno.")
    String requiredScopes;
    @Label("Activa")
    boolean active = true;

    @Section("Dónde se usa")
    @ReadOnly
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    @Stereotype(FieldStereotype.textarea)
    String uses;

    final UiSourceCommands commands;
    final AudienceCommands audienceCommands;
    final NamedAudiences namedAudiences;
    final Readers readers;

    public UiSourceForm(UiSourceCommands commands, AudienceCommands audienceCommands, NamedAudiences namedAudiences,
                        Readers readers) {
        this.commands = commands;
        this.audienceCommands = audienceCommands;
        this.namedAudiences = namedAudiences;
        this.readers = readers;
    }

    public UiSourceForm load(UiSource s) {
        id = s.id();
        name = s.name();
        kind = s.kind();
        basePath = s.basePath();
        tagName = s.tagName();
        requiredScopes = String.join(" ", s.requiredScopes());
        active = s.active();
        audience(s.audience(), audienceCommands, namedAudiences);
        var list = commands.uses(s.id());
        uses = list.isEmpty() ? "Sin usos: ninguna página ni bloque la muestra" : String.join("\n", list);
        return this;
    }

    UiSource source() {
        return new UiSource(id == null ? null : id.trim(), name, kind, basePath,
                kind == UiSourceKind.WEB_COMPONENT ? tagName : null, audience(), scopes(requiredScopes), active);
    }

    static Set<String> scopes(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(text.trim().split("[\\s,]+")));
    }

    @Override
    public String create(HttpRequest httpRequest) {
        commands.register(readers.of(httpRequest), source());
        return id;
    }

    @Override
    public void save(HttpRequest httpRequest) {
        commands.update(readers.of(httpRequest), source());
    }

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        return "tagName".equals(fieldName) && kind != UiSourceKind.WEB_COMPONENT;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nueva UI externa" : name;
    }
}
