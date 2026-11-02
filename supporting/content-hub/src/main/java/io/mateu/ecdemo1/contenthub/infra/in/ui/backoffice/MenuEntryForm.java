package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.menu.SiteMenus;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuEntry;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An entry of a site's menu: where it sits (inside which submenu, at which position), what it says in
 * each language, where it goes, who sees it («Quién lo verá», as for the content) and whether it is
 * active. Without a destination it is a submenu. The toolbar moves a saved entry one step at a time.
 */
@Service
@Scope("prototype")
@ContentAdmins
public class MenuEntryForm implements SimpleCrud.CrudForm, VisibilitySupplier, io.mateu.uidl.interfaces.OptionsSupplier {

    /** The destination select's value for "none": the entry is a submenu. */
    static final String SUBMENU = "";

    @Section("Entrada")
    @NotEmpty
    @EditableOnlyWhenCreating
    @Label("Menú del sitio")
    @Stereotype(FieldStereotype.select)
    String site = SiteMenu.INTRANET;
    @Label("Dentro de")
    @Help("Vacío: en el primer nivel del menú. Solo un submenú (una entrada sin destino) puede tener entradas.")
    @Stereotype(FieldStereotype.select)
    String parentId;
    @Label("Posición entre sus hermanas (0 = primera)")
    int position = 999;
    @Label("Activa")
    boolean active = true;

    @Section("Texto")
    @NotEmpty
    @Label("Texto (es)")
    String labelEs;
    @Label("Text (en)")
    String labelEn;
    @Label("Text (de)")
    String labelDe;

    @Section("Destino")
    @Label("Destino")
    @Help("Una función del portal, una página, una sección u otra interfaz. Sin destino ni enlace, la entrada es un submenú.")
    @Stereotype(FieldStereotype.select)
    String target = SUBMENU;
    @Label("…o un enlace: ruta del portal (/intranet/…) o URL http(s)")
    @Help("Una URL de fuera de la intranet se abre en una pestaña nueva.")
    String url;
    @Label("Ruta dentro de la otra interfaz (opcional)")
    String remoteRoute;

    // «Quién lo verá», last: optional (empty = everyone). AudienceForm's fields, declared here because a
    // superclass's fields are drawn first.
    @Section("Audiencia (opcional)")
    @Label("Audiencia con nombre")
    @Stereotype(FieldStereotype.select)
    String namedAudience;
    @Label("Empresa")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> companies;
    @Label("País")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> countries;
    @Label("Región")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> regions;
    @Label("Hotel")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> hotels;
    @Label("Departamento")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> departments;
    @Label("Puesto")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> jobTitles;
    @io.mateu.uidl.annotations.ReadOnly
    @Label("Quién lo verá")
    @io.mateu.uidl.annotations.Colspan(2)
    String audienceSummary;
    @io.mateu.uidl.annotations.Hidden
    String advancedAudience;

    @io.mateu.uidl.annotations.Hidden
    String id;

    final SiteMenus menus;
    final AudienceCommands audienceCommands;
    final NamedAudiences namedAudiences;
    final Readers readers;

    public MenuEntryForm(SiteMenus menus, AudienceCommands audienceCommands, NamedAudiences namedAudiences,
                         Readers readers) {
        this.menus = menus;
        this.audienceCommands = audienceCommands;
        this.namedAudiences = namedAudiences;
        this.readers = readers;
    }

    Audience audience() {
        var values = new EnumMap<Attribute, List<String>>(Attribute.class);
        values.put(Attribute.COMPANY, companies);
        values.put(Attribute.COUNTRY, countries);
        values.put(Attribute.REGION, regions);
        values.put(Attribute.HOTEL, hotels);
        values.put(Attribute.DEPARTMENT, departments);
        values.put(Attribute.JOB_TITLE, jobTitles);
        return AudienceFields.build(namedAudience, values, advancedAudience);
    }

    void audience(Audience audience) {
        var parsed = AudienceFields.parse(audience);
        namedAudience = parsed.named();
        companies = parsed.values().get(Attribute.COMPANY);
        countries = parsed.values().get(Attribute.COUNTRY);
        regions = parsed.values().get(Attribute.REGION);
        hotels = parsed.values().get(Attribute.HOTEL);
        departments = parsed.values().get(Attribute.DEPARTMENT);
        jobTitles = parsed.values().get(Attribute.JOB_TITLE);
        advancedAudience = parsed.advanced();
        var count = audienceCommands.count(audience);
        audienceSummary = audience.describe(namedAudiences.labels()) + " — " + count + " empleado(s)"
                + (count == 0 ? " ⚠️ nadie cumple esta audiencia" : "");
    }

    /** A row's id: the site and the entry («intranet~noticias»). */
    static String rowId(String site, String entryId) {
        return site + "~" + entryId;
    }

    static String[] split(String rowId) {
        var i = rowId.indexOf('~');
        return i < 0 ? new String[]{SiteMenu.INTRANET, rowId} : new String[]{rowId.substring(0, i), rowId.substring(i + 1)};
    }

    public MenuEntryForm load(String site, MenuEntry e) {
        this.site = site;
        id = e.id();
        parentId = e.parentId();
        position = e.order();
        active = e.active();
        labelEs = e.labels().get("es");
        labelEn = e.labels().get("en");
        labelDe = e.labels().get("de");
        var t = e.target();
        target = SUBMENU;
        url = null;
        remoteRoute = null;
        if (t != null && MenuTarget.URL.equals(t.kind())) {
            url = t.value();
        } else if (t != null && MenuTarget.REMOTE.equals(t.kind())) {
            target = MenuTarget.REMOTE + ":" + t.remoteSource();
            remoteRoute = t.remoteRoute();
        } else if (t != null) {
            target = t.kind() + ":" + t.value();
        }
        audience(e.audience());
        return this;
    }

    Map<String, String> labels() {
        var labels = new LinkedHashMap<String, String>();
        labels.put("es", labelEs);
        labels.put("en", labelEn);
        labels.put("de", labelDe);
        return labels;
    }

    MenuTarget menuTarget() {
        if (url != null && !url.isBlank()) {
            return MenuTarget.url(url);
        }
        if (target == null || target.isBlank()) {
            return null;
        }
        var colon = target.indexOf(':');
        var kind = target.substring(0, colon);
        var value = target.substring(colon + 1);
        return MenuTarget.REMOTE.equals(kind) ? MenuTarget.remote(value, remoteRoute) : new MenuTarget(kind, value);
    }

    @Override
    public String create(HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        id = menus.add(reader, site, parentId, labels(), menuTarget(), audience(), active);
        menus.move(reader, site, id, parentId, position);
        return rowId(site, id);
    }

    @Override
    public void save(HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        menus.update(reader, site, id, labels(), menuTarget(), audience(), active);
        menus.move(reader, site, id, parentId, position);
    }

    // ── one step at a time ──────────────────────────────────────────────────

    @Toolbar
    @Action
    public Object subir(HttpRequest httpRequest) {
        return step(httpRequest, "Subida", id -> menus.moveUp(readers.of(httpRequest), site, id));
    }

    @Toolbar
    @Action
    public Object bajar(HttpRequest httpRequest) {
        return step(httpRequest, "Bajada", id -> menus.moveDown(readers.of(httpRequest), site, id));
    }

    @Toolbar
    @Action
    @Label("Meter en el submenú de encima")
    public Object meterEnElSubmenuDeEncima(HttpRequest httpRequest) {
        return step(httpRequest, "Ahora está en el submenú de encima", id -> menus.indent(readers.of(httpRequest), site, id));
    }

    @Toolbar
    @Action
    @Label("Sacar del submenú")
    public Object sacarDelSubmenu(HttpRequest httpRequest) {
        return step(httpRequest, "Ahora está detrás de su submenú", id -> menus.outdent(readers.of(httpRequest), site, id));
    }

    Object step(HttpRequest httpRequest, String done, Consumer<String> operation) {
        if (id == null) {
            return Message.warning("Guarda la entrada primero");
        }
        try {
            operation.accept(id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Message.error(e.getMessage());
        }
        var e = menus.menu(site).find(id).orElseThrow();
        parentId = e.parentId();
        position = e.order();
        return Message.success(done + " (posición " + position + ")");
    }

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        return id == null && List.of("subir", "bajar", "meterEnElSubmenuDeEncima", "sacarDelSubmenu").contains(fieldName);
    }

    // ── selects ────────────────────────────────────────────────────────────

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return List.of("site", "parentId", "target").contains(fieldName) || "namedAudience".equals(fieldName)
                || AudienceFields.FIELDS.containsKey(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        var siteId = site == null || site.isBlank() ? SiteMenu.INTRANET : site;
        return switch (fieldName) {
            case "site" -> menus.sites().stream().map(s -> new Option(s, s)).toList();
            case "parentId" -> {
                var menu = menus.menu(siteId);
                yield menu.entries().stream().filter(MenuEntry::isSubmenu).filter(e -> !e.id().equals(id))
                        .filter(e -> id == null || !menu.ancestors(e.id()).contains(id))
                        .map(e -> new Option(e.id(), "  ".repeat(menu.depth(e.id())) + e.label("es", menu.defaultLanguage())))
                        .toList();
            }
            case "target" -> {
                var out = new ArrayList<Option>();
                out.add(new Option(SUBMENU, "— Ninguno: es un submenú —"));
                for (var kind : menus.targetKinds(siteId)) {
                    for (var c : kind.choices(siteId)) {
                        out.add(new Option(kind.kind() + ":" + c.value(), kind.label() + ": " + c.label()));
                    }
                }
                yield out;
            }
            case "namedAudience" -> Catalog.get().namedAudiences();
            default -> AudienceFields.options(fieldName);
        };
    }

    @Override
    public String id() {
        return id == null ? null : rowId(site, id);
    }

    @Override
    public String toString() {
        return id == null ? "Nueva entrada de menú" : labelEs;
    }
}
