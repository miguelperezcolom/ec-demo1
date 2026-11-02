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
import io.mateu.uidl.annotations.Help;
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
    /** The site it belongs to (a newsletter's pages and articles are its own): its sections are that site's. */
    @io.mateu.uidl.annotations.Hidden
    String siteId;
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
    @Label("URL (enlace, documento, fichero de Google Drive, Docs, Sheets o Slides; o media:id)")
    String url;
    @Label("Seguimiento de lectura")
    boolean readTracking;
    @Label("Confirmación obligatoria")
    boolean confirmationRequired;
    @Label("Confirmar antes del")
    LocalDateTime confirmationDeadline;
    @Label("Al publicar, exigir reconfirmar")
    boolean requireReconfirmation;
    @Label("Comentarios cerrados")
    @io.mateu.uidl.annotations.Help("Nadie más puede comentar; los comentarios que hay se siguen viendo. Se aplica al guardar, sin esperar a publicar.")
    boolean comentariosCerrados;

    @Label("Notificar a los lectores al publicar")
    @Help("Por defecto, los avisos notifican y el resto de contenidos no.")
    @Stereotype(FieldStereotype.select)
    String notificar = NOTIFY_BY_TYPE;
    @Label("Importante (avisar también por correo)")
    boolean importante;

    @Section("Pantalla externa")
    @Label("UI externa")
    @Help("Una de las UIs registradas en Configuración → UIs externas. La página la muestra en vivo, en su propia URL.")
    @Stereotype(FieldStereotype.select)
    String uiSourceId;
    @Label("Ruta (una UI de Mateu o un iframe: /loyalty/members…)")
    String uiRoute;
    @Label("Atributos (un web component: nombre=valor, uno por línea)")
    @Stereotype(FieldStereotype.textarea)
    String uiAttributes;

    @Section("Bloques (solo páginas)")
    @Label("Bloques")
    List<BlockForm> blocks = new ArrayList<>();

    @Section("Medios y vista previa")
    @Label("Medio de la biblioteca para insertar")
    @Stereotype(FieldStereotype.select)
    String medioAInsertar;

    @Label("Vista previa (idioma por defecto)")
    @Colspan(2)
    java.util.concurrent.Callable<io.mateu.uidl.fluent.Component> vistaPrevia = this::preview;

    @Section("Avisos")
    @ReadOnly
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    String warnings;

    @Section("Cifras")
    @ReadOnly
    @HiddenInCreate
    @Label("Vistas")
    String vistas;
    @ReadOnly
    @HiddenInCreate
    @Label("Lectores únicos")
    String lectoresUnicos;
    @ReadOnly
    @HiddenInCreate
    @Label("Confirmaciones")
    String confirmaciones;
    @ReadOnly
    @HiddenInCreate
    @Label("Lectores notificados")
    String notificados;

    static final java.util.Set<String> REMOTE_PAGE_FIELDS = java.util.Set.of("uiSourceId", "uiRoute", "uiAttributes");

    @Section("Enlaces de vista previa")
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    java.util.concurrent.Callable<io.mateu.uidl.fluent.Component> enlacesDeVistaPrevia = this::previewLinks;

    static final String NOTIFY_BY_TYPE = "POR_TIPO";
    static final String NOTIFY_YES = "SI";
    static final String NOTIFY_NO = "NO";

    final ItemCommands commands;
    final ItemRepository items;
    final ContentIndex index;
    final AudienceCommands audienceCommands;
    final NamedAudiences namedAudiences;
    final Readers readers;
    final io.mateu.ecdemo1.contenthub.application.out.Translator translator;
    final io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer renderer;
    final io.mateu.ecdemo1.contenthub.application.usecases.Collaboration collaboration;
    final io.mateu.ecdemo1.contenthub.application.usecases.UsageAnalytics analytics;
    final io.mateu.ecdemo1.contenthub.application.usecases.Templates templates;
    final io.mateu.ecdemo1.contenthub.application.usecases.PreviewLinks previewLinks;

    public ItemForm(ItemCommands commands, ItemRepository items, ContentIndex index, AudienceCommands audienceCommands,
                    NamedAudiences namedAudiences, Readers readers,
                    io.mateu.ecdemo1.contenthub.application.out.Translator translator,
                    io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer renderer,
                    io.mateu.ecdemo1.contenthub.application.usecases.Collaboration collaboration,
                    io.mateu.ecdemo1.contenthub.application.usecases.UsageAnalytics analytics,
                    io.mateu.ecdemo1.contenthub.application.usecases.Templates templates,
                    io.mateu.ecdemo1.contenthub.application.usecases.PreviewLinks previewLinks) {
        this.templates = templates;
        this.previewLinks = previewLinks;
        this.commands = commands;
        this.items = items;
        this.index = index;
        this.audienceCommands = audienceCommands;
        this.namedAudiences = namedAudiences;
        this.readers = readers;
        this.translator = translator;
        this.renderer = renderer;
        this.collaboration = collaboration;
        this.analytics = analytics;
    }

    public ItemForm load(Item item, boolean commentsClosed) {
        id = item.id();
        siteId = item.siteId();
        comentariosCerrados = commentsClosed;
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
        io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi.parse(c.url()).ifPresent(r -> {
            uiSourceId = r.sourceId();
            uiRoute = r.route();
            uiAttributes = r.attributeLines();
            url = null;
        });
        readTracking = c.readTracking();
        confirmationRequired = c.confirmationRequired();
        confirmationDeadline = Ui.local(c.confirmationDeadline());
        notificar = c.notifyReaders() == null ? NOTIFY_BY_TYPE : c.notifyReaders() ? NOTIFY_YES : NOTIFY_NO;
        importante = c.important();
        blocks = new ArrayList<>(c.blocks().stream().map(b -> BlockForm.of(b, item.defaultLanguage())).toList());
        audience(c.audience(), audienceCommands, namedAudiences);
        estado = status(item);
        var figures = analytics.ofItem(item.id(), item.confirmationVersion());
        vistas = String.valueOf(figures.views());
        lectoresUnicos = String.valueOf(figures.uniqueReaders());
        confirmaciones = String.valueOf(figures.confirmations());
        notificados = String.valueOf(figures.notified());
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
                type == ItemType.PAGE && blocks != null ? blocks.stream().map(b -> b.toBlock(language())).toList() : List.of(),
                Ui.instant(publishAt), Ui.instant(expireAt), readTracking, confirmationRequired,
                Ui.instant(confirmationDeadline), url())
                .withNotification(NOTIFY_YES.equals(notificar) ? Boolean.TRUE : NOTIFY_NO.equals(notificar) ? Boolean.FALSE : null,
                        importante);
    }

    /** The item's default language — its blocks' own text is this language's. */
    String language() {
        return defaultLanguage == null || defaultLanguage.isBlank() ? "es" : defaultLanguage;
    }

    /** The URL as stored: a remote page's is its UI (ui:source/route), the rest as typed. */
    String url() {
        if (type == ItemType.REMOTE_PAGE) {
            if (uiSourceId == null || uiSourceId.isBlank()) {
                throw new IllegalArgumentException("Elige la UI externa de la pantalla");
            }
            return new io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi(uiSourceId.trim(), uiRoute,
                    io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi.attributesOf(uiAttributes)).uri();
        }
        return url == null || url.isBlank() ? null : url.trim();
    }

    public String create(HttpRequest httpRequest) {
        var editor = readers.of(httpRequest);
        var created = siteId == null || io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET.equals(siteId)
                ? commands.create(editor, type, defaultLanguage, content())
                : commands.createInSite(editor, siteId, type, defaultLanguage, content());
        collaboration.closeComments(editor, created, comentariosCerrados);
        return created;
    }

    public void save(HttpRequest httpRequest) {
        var editor = readers.of(httpRequest);
        commands.edit(editor, id, content());
        // kept with the item's comments, not in its content: it acts at once, without a new version
        collaboration.closeComments(editor, id, comentariosCerrados);
    }

    /**
     * Fills the languages that have no title yet from the default language's text, with the model —
     * in the form only: the editor reviews it and saves (nothing is saved or published from here).
     */
    @io.mateu.uidl.annotations.Button
    @Action
    public Object traducirConIA(HttpRequest httpRequest) {
        readers.of(httpRequest).require(io.mateu.ecdemo1.contenthub.application.security.EditorialPermission.EDIT);
        var from = defaultLanguage == null ? "es" : defaultLanguage;
        var source = text(from);
        if (source.title().isBlank()) {
            return Message.warning("Escribe primero el título en el idioma por defecto (" + from + ")");
        }
        var done = new java.util.ArrayList<String>();
        for (var to : List.of("es", "en", "de")) {
            if (to.equals(from) || !text(to).title().isBlank()) {
                continue;
            }
            try {
                set(to, translator.translate(source, from, to, httpRequest.getHeaderValue("Authorization")));
                done.add(to);
            } catch (RuntimeException e) {
                return Message.error("No se ha podido traducir a «" + to + "»: " + e.getMessage());
            }
        }
        // a page's blocks: what each language lacks of them, from the default language's
        var blocksDone = new java.util.ArrayList<String>();
        if (type == ItemType.PAGE && blocks != null && !blocks.isEmpty()) {
            var current = blocks.stream().map(b -> b.toBlock(from)).toList();
            for (var to : List.of("es", "en", "de")) {
                if (to.equals(from)) {
                    continue;
                }
                try {
                    var result = io.mateu.ecdemo1.contenthub.application.usecases.BlockTranslation.translate(current, from,
                            to, source.title(), translator, httpRequest.getHeaderValue("Authorization"));
                    if (result.parts() > 0) {
                        current = result.blocks();
                        blocksDone.add(to);
                    }
                } catch (RuntimeException e) {
                    return Message.error("No se han podido traducir los bloques a «" + to + "»: " + e.getMessage());
                }
            }
            blocks = new ArrayList<>(current.stream().map(b -> BlockForm.of(b, from)).toList());
        }
        if (done.isEmpty() && blocksDone.isEmpty()) {
            return Message.warning("Todos los idiomas tienen ya título" + (type == ItemType.PAGE ? " y los bloques, su texto" : "")
                    + ": vacía uno para traducirlo");
        }
        // the form with the translated fields (its state goes back only when the action returns it),
        // and the message
        var what = new java.util.ArrayList<String>();
        if (!done.isEmpty()) {
            what.add("el contenido a " + String.join(", ", done));
        }
        if (!blocksDone.isEmpty()) {
            what.add("los bloques a " + String.join(", ", blocksDone));
        }
        return List.of(this, Message.success("Traducido con IA " + String.join(" y ", what) + ". Revísalo y guarda."));
    }

    /** Puts the chosen medium at the end of the default language's body, where the editor moves it to. */
    @io.mateu.uidl.annotations.Button
    @Action
    public Object insertarMedio(HttpRequest httpRequest) {
        readers.of(httpRequest).require(io.mateu.ecdemo1.contenthub.application.security.EditorialPermission.EDIT);
        if (medioAInsertar == null || medioAInsertar.isBlank()) {
            return Message.warning("Elige primero el medio, en «Medios y vista previa»");
        }
        var language = defaultLanguage == null ? "es" : defaultLanguage;
        var t = text(language);
        var body = t.body() == null ? "" : t.body();
        set(language, new io.mateu.ecdemo1.contenthub.domain.item.Translation(t.title(), t.summary(),
                body + "<p>[[media:" + medioAInsertar + "]]</p>"));
        medioAInsertar = null;
        return List.of(this, Message.success("Medio insertado al final del cuerpo (" + language + "). Muévelo donde quieras y guarda."));
    }

    /** Draws the preview again with what is in the form now (nothing is saved). */
    @io.mateu.uidl.annotations.Button
    @Action
    public Object previsualizar(HttpRequest httpRequest) {
        return this;
    }

    /**
     * The default language's text as a reader will see it — title, summary and body with the media
     * expanded (signed for the editor). Blocks and audience: «Ver como».
     */
    io.mateu.uidl.fluent.Component preview() {
        var t = text(defaultLanguage == null ? "es" : defaultLanguage);
        var html = "<article style=\"max-width:860px;border:1px dashed rgba(128,128,128,.4);border-radius:12px;padding:1rem 1.25rem\">"
                + "<h1 style=\"margin-top:0\">" + Ui.esc(t.title()) + "</h1>"
                + (t.summary() == null || t.summary().isBlank() ? "" : "<p style=\"font-size:1.1rem;opacity:.85\">" + Ui.esc(t.summary()) + "</p>")
                + renderer.body(t.body(), io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer.PORTAL_BASE,
                io.mateu.ecdemo1.contenthub.infra.in.render.MediaUrls.EDITOR)
                + "</article>";
        return io.mateu.ecdemo1.uicommons.html.Html.block(html);
    }

    io.mateu.ecdemo1.contenthub.domain.item.Translation text(String language) {
        return switch (language) {
            case "en" -> new io.mateu.ecdemo1.contenthub.domain.item.Translation(titleEn, summaryEn, bodyEn);
            case "de" -> new io.mateu.ecdemo1.contenthub.domain.item.Translation(titleDe, summaryDe, bodyDe);
            default -> new io.mateu.ecdemo1.contenthub.domain.item.Translation(titleEs, summaryEs, bodyEs);
        };
    }

    void set(String language, io.mateu.ecdemo1.contenthub.domain.item.Translation t) {
        switch (language) {
            case "en" -> { titleEn = t.title(); summaryEn = t.summary(); bodyEn = t.body(); }
            case "de" -> { titleDe = t.title(); summaryDe = t.summary(); bodyDe = t.body(); }
            default -> { titleEs = t.title(); summaryEs = t.summary(); bodyEs = t.body(); }
        }
    }

    /** A page, edited on the page itself: the visual editor (VisualEditorPage). Saving there replaces the draft's blocks. */
    @Toolbar
    @Action
    public Object editarSobreLaPagina(HttpRequest httpRequest) {
        return io.mateu.uidl.data.UICommand.navigateTo(VisualEditorPage.route(id));
    }

    /** Every step of the item, two of its states compared, an earlier one restored (HistoryPage). */
    @Toolbar
    @Action
    public Object historial(HttpRequest httpRequest) {
        return io.mateu.uidl.data.UICommand.navigateTo(HistoryPage.route(id));
    }

    /** The draft, to a reviewer: this item's preview links (PreviewLinksPage), to make, follow and revoke. */
    @Toolbar
    @Action
    @Label("Enlace de vista previa")
    public Object enlaceDeVistaPrevia(HttpRequest httpRequest) {
        return io.mateu.uidl.data.UICommand.navigateTo(PreviewLinksPage.route(id));
    }

    /** The page draft's blocks and audience as a new template (a publisher's); opens it to name and describe it. */
    @Toolbar
    @Publishers
    @Action
    @Label("Guardar como plantilla")
    public Object guardarComoPlantilla(HttpRequest httpRequest) {
        var title = titleEs != null && !titleEs.isBlank() ? titleEs : toString();
        var created = templates.saveFromPage(readers.of(httpRequest), id, "Plantilla: " + title,
                "Creada a partir de la página «" + title + "»");
        return io.mateu.uidl.data.UICommand.navigateTo(TemplatesCrud.route(created));
    }

    io.mateu.uidl.fluent.Component previewLinks() {
        return io.mateu.ecdemo1.uicommons.html.Html.block(PreviewLinksPage.list(previewLinks, id)
                + "<p style=\"font-size:.85rem;opacity:.75\">Créalos y revócalos con «Enlace de vista previa».</p>");
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
        if ("traducirConIA".equals(fieldName)) {
            return !translator.available();
        }
        if (REMOTE_PAGE_FIELDS.contains(fieldName)) {
            return type != ItemType.REMOTE_PAGE;
        }
        if (("url".equals(fieldName) || "blocks".equals(fieldName)) && type == ItemType.REMOTE_PAGE) {
            return true;
        }
        if (id == null) {
            return List.of("enviarARevision", "publicar", "rechazar", "retirar", "editarSobreLaPagina", "historial",
                    "guardarComoPlantilla", "enlaceDeVistaPrevia").contains(fieldName);
        }
        if ("editarSobreLaPagina".equals(fieldName) || "guardarComoPlantilla".equals(fieldName)) {
            return type != ItemType.PAGE;
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
        return "sectionId".equals(fieldName) || "defaultLanguage".equals(fieldName) || "medioAInsertar".equals(fieldName)
                || "notificar".equals(fieldName) || "uiSourceId".equals(fieldName)
                || super.supports(fieldType, fieldName, formType);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "sectionId" -> Catalog.get().sections(io.mateu.ecdemo1.contenthub.domain.site.Site.of(siteId));
            case "defaultLanguage" -> Catalog.languages();
            case "medioAInsertar" -> Catalog.get().media();
            case "uiSourceId" -> Catalog.get().uiSources();
            case "notificar" -> List.of(new Option(NOTIFY_BY_TYPE, "Según el tipo (los avisos, sí)"),
                    new Option(NOTIFY_YES, "Sí"), new Option(NOTIFY_NO, "No"));
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
