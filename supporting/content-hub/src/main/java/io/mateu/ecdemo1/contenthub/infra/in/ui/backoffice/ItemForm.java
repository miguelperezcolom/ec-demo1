package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Publishers;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * An item in the backoffice: the draft the editor works on, its translations, its audience (the visual
 * builder), its dates and flags, and — for a page — its blocks. The toolbar walks the workflow; a
 * publisher publishes, with or without review.
 */
@Service
@Scope("prototype")
@Editors
public class ItemForm extends AudienceForm implements Identifiable, VisibilitySupplier {

    @ReadOnly
    @HiddenInCreate
    Status estado;
    @ReadOnly
    @HiddenInCreate
    @Label("Id")
    String id;
    @ReadOnly
    @HiddenInCreate
    @Label("Aplicado a los lectores")
    String applied;

    @Section("Contenido")
    @NotNull
    @EditableOnlyWhenCreating
    @Label("Tipo")
    ItemType type = ItemType.ARTICLE;
    @Label("Sección")
    @Stereotype(FieldStereotype.select)
    String sectionId;
    @Label("Idioma por defecto")
    @Stereotype(FieldStereotype.select)
    String defaultLanguage = "es";

    @Section("Español")
    @Label("Título")
    String titleEs;
    @Label("Resumen")
    @Stereotype(FieldStereotype.textarea)
    String summaryEs;
    @Label("Cuerpo (incrusta medios con [[media:id]])")
    @Stereotype(FieldStereotype.richText)
    @Colspan(2)
    String bodyEs;

    @Section("English")
    @Label("Title")
    String titleEn;
    @Label("Summary")
    @Stereotype(FieldStereotype.textarea)
    String summaryEn;
    @Label("Body")
    @Stereotype(FieldStereotype.richText)
    @Colspan(2)
    String bodyEn;

    @Section("Deutsch")
    @Label("Titel")
    String titleDe;
    @Label("Zusammenfassung")
    @Stereotype(FieldStereotype.textarea)
    String summaryDe;
    @Label("Text")
    @Stereotype(FieldStereotype.richText)
    @Colspan(2)
    String bodyDe;

    @Section("Publicación")
    @Label("Publicar a partir de (vacío: al publicar)")
    LocalDateTime publishAt;
    @Label("Caduca el")
    LocalDateTime expireAt;
    @Label("URL (enlace o documento; o media:id)")
    String url;
    @Label("Seguimiento de lectura")
    boolean readTracking;
    @Label("Confirmación obligatoria")
    boolean confirmationRequired;
    @Label("Confirmar antes del")
    LocalDateTime confirmationDeadline;
    @Label("Al publicar, exigir reconfirmar")
    boolean requireReconfirmation;

    @Section("Bloques (solo páginas)")
    @Label("Bloques")
    List<BlockForm> blocks = new ArrayList<>();

    @Section("Avisos")
    @ReadOnly
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    String warnings;

    final ItemCommands commands;
    final ItemRepository items;
    final ContentIndex index;
    final AudienceCommands audienceCommands;
    final NamedAudiences namedAudiences;
    final Readers readers;

    public ItemForm(ItemCommands commands, ItemRepository items, ContentIndex index, AudienceCommands audienceCommands,
                    NamedAudiences namedAudiences, Readers readers) {
        this.commands = commands;
        this.items = items;
        this.index = index;
        this.audienceCommands = audienceCommands;
        this.namedAudiences = namedAudiences;
        this.readers = readers;
    }

    public ItemForm load(Item item) {
        id = item.id();
        type = item.type();
        defaultLanguage = item.defaultLanguage();
        var c = item.draft();
        sectionId = c.sectionId();
        var es = c.translations().get("es");
        var en = c.translations().get("en");
        var de = c.translations().get("de");
        titleEs = es == null ? null : es.title();
        summaryEs = es == null ? null : es.summary();
        bodyEs = es == null ? null : es.body();
        titleEn = en == null ? null : en.title();
        summaryEn = en == null ? null : en.summary();
        bodyEn = en == null ? null : en.body();
        titleDe = de == null ? null : de.title();
        summaryDe = de == null ? null : de.summary();
        bodyDe = de == null ? null : de.body();
        publishAt = Ui.local(c.publishAt());
        expireAt = Ui.local(c.expireAt());
        url = c.url();
        readTracking = c.readTracking();
        confirmationRequired = c.confirmationRequired();
        confirmationDeadline = Ui.local(c.confirmationDeadline());
        blocks = new ArrayList<>(c.blocks().stream().map(BlockForm::of).toList());
        audience(c.audience(), audienceCommands, namedAudiences);
        estado = status(item);
        applied = item.latestVersion().map(v -> index.applied(item.id())
                .map(a -> a.version() >= v.number() ? "Sí — versión " + v.number() + " aplicada el " + Ui.when(a.at())
                        : "Aplicando la versión " + v.number() + "…")
                .orElse("Aplicando la versión " + v.number() + "…")).orElse("Aún no publicado");
        var w = commands.warnings(c);
        warnings = w.isEmpty() ? "Sin avisos" : "⚠️ " + String.join("\n⚠️ ", w);
        return this;
    }

    static Status status(Item item) {
        var text = item.status().label() + (item.draftInReview() ? " · cambios en revisión"
                : item.hasVersions() && item.draftChanged() ? " · cambios sin publicar" : "");
        var type = switch (item.status()) {
            case DRAFT -> StatusType.NONE;
            case IN_REVIEW -> StatusType.WARNING;
            case PUBLISHED -> StatusType.SUCCESS;
            case RETIRED -> StatusType.DANGER;
        };
        return new Status(type, text);
    }

    ItemContent content() {
        var translations = new LinkedHashMap<String, Translation>();
        if (titleEs != null && !titleEs.isBlank()) {
            translations.put("es", new Translation(titleEs, summaryEs, bodyEs));
        }
        if (titleEn != null && !titleEn.isBlank()) {
            translations.put("en", new Translation(titleEn, summaryEn, bodyEn));
        }
        if (titleDe != null && !titleDe.isBlank()) {
            translations.put("de", new Translation(titleDe, summaryDe, bodyDe));
        }
        return new ItemContent(sectionId == null || sectionId.isBlank() ? null : sectionId, audience(), translations,
                type == ItemType.PAGE && blocks != null ? blocks.stream().map(BlockForm::toBlock).toList() : List.of(),
                Ui.instant(publishAt), Ui.instant(expireAt), readTracking, confirmationRequired,
                Ui.instant(confirmationDeadline), url == null || url.isBlank() ? null : url.trim());
    }

    public String create(HttpRequest httpRequest) {
        return commands.create(readers.of(httpRequest), type, defaultLanguage, content());
    }

    public void save(HttpRequest httpRequest) {
        commands.edit(readers.of(httpRequest), id, content());
    }

    @Toolbar
    @Action
    public Object enviarARevision(HttpRequest httpRequest) {
        commands.submitForReview(readers.of(httpRequest), id);
        return Message.success("Enviado a revisión");
    }

    @Toolbar
    @Publishers
    @Action(confirmationRequired = true, confirmationTitle = "¿Publicar?",
            confirmationMessage = "Se congela el borrador en una versión nueva y la ven sus lectores desde su fecha de publicación.")
    public Object publicar(HttpRequest httpRequest) {
        commands.publish(readers.of(httpRequest), id, requireReconfirmation);
        return Message.success("Publicado. Los lectores lo verán en unos segundos (columna «Aplicado»).");
    }

    @Toolbar
    @Publishers
    @Action
    public Object rechazar(HttpRequest httpRequest) {
        commands.reject(readers.of(httpRequest), id);
        return Message.warning("Devuelto al editor");
    }

    @Toolbar
    @Publishers
    @Action(confirmationRequired = true, confirmationTitle = "¿Retirar?",
            confirmationMessage = "Deja de verse en todos los consumidores: portal, menús, búsqueda, slots, newsletter y chat.")
    public Object retirar(HttpRequest httpRequest) {
        commands.retire(readers.of(httpRequest), id);
        return Message.success("Retirado");
    }

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        if (id == null) {
            return List.of("enviarARevision", "publicar", "rechazar", "retirar").contains(fieldName);
        }
        var item = items.find(id).orElse(null);
        if (item == null) {
            return false;
        }
        return switch (fieldName) {
            case "enviarARevision" -> item.awaitingPublication() || (item.hasVersions() && !item.draftChanged());
            // nothing new to publish: a published item with no changes since its last version
            case "publicar" -> item.status() == ItemStatus.PUBLISHED && !item.draftChanged() && !item.awaitingPublication();
            case "rechazar" -> !item.awaitingPublication();
            case "retirar" -> item.status() != ItemStatus.PUBLISHED;
            default -> false;
        };
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "sectionId".equals(fieldName) || "defaultLanguage".equals(fieldName)
                || super.supports(fieldType, fieldName, formType);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "sectionId" -> Catalog.get().sections();
            case "defaultLanguage" -> Catalog.languages();
            default -> super.options(fieldName, httpRequest);
        };
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nuevo contenido" : (titleEs != null ? titleEs : titleEn != null ? titleEn : id);
    }
}
