package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterTracking;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterEditorial;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Cadence;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * A newsletter: a site of its own — identity, audience, languages, its sections and its pages — and its
 * issues: the layout each one starts with, where each section's automatic content comes from, and the cadence
 * (a draft of the next issue appears when one is due; sent as it is only if said so). Its issues and pages
 * are listed below, each opening its own screen.
 */
@Service
@Scope("prototype")
@Editors
public class NewsletterForm extends AudienceForm implements SimpleCrud.CrudForm {

    @Section("Newsletter")
    @ReadOnly
    @HiddenInCreate
    @Label("Id")
    String id;
    @NotEmpty
    @Label("Nombre")
    String name;
    @Label("Descripción")
    @Stereotype(FieldStereotype.textarea)
    String description;
    @Label("Idiomas en que se escribe (vacío: el de cada lector)")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> languages = new ArrayList<>(List.of("es"));
    @HiddenInCreate
    @Label("Activa")
    boolean active = true;

    @Section("Identidad")
    @Label("Logo (imagen de la biblioteca de medios)")
    @Stereotype(FieldStereotype.select)
    String logoMediaId;
    @Label("Color principal (#RRGGBB)")
    String primaryColor = Site.Theme.DEFAULT.primary();
    @Label("Color de los enlaces (#RRGGBB)")
    String accentColor = Site.Theme.DEFAULT.accent();
    @Label("Cabecera")
    @Stereotype(FieldStereotype.richText)
    @Colspan(2)
    String headerHtml;
    @Label("Pie")
    @Stereotype(FieldStereotype.richText)
    @Colspan(2)
    String footerHtml;

    @Section("Secciones")
    @Label("Secciones (las llena cada número)")
    @Colspan(2)
    List<NewsletterSectionForm> sections = new ArrayList<>(List.of(NewsletterSectionForm.of("noticias", "Noticias", List.of())));

    @Section("Maqueta de cada número")
    @Label("Bloques con los que empieza cada número (vacío: una introducción y un bloque por sección)")
    @Colspan(2)
    List<BlockForm> layout = new ArrayList<>();

    @Section("Periodicidad")
    @Label("Periodicidad")
    @Stereotype(FieldStereotype.select)
    String frequency = "NONE";
    @Label("Día de la semana (semanal)")
    @Stereotype(FieldStereotype.select)
    String dayOfWeek = "MONDAY";
    @Label("Día del mes (mensual, 1–31)")
    Integer dayOfMonth = 1;
    @Label("Hora (HH:mm, hora de Madrid)")
    String time = "09:00";
    @Label("Enviar cada número sin revisión")
    @Help("Sin marcar (lo normal): el número queda en borrador para que un editor lo revise y lo programe. Marcarlo exige content:publish.")
    boolean autoSend;
    @ReadOnly
    @HiddenInCreate
    @Label("Próximo número")
    String next;

    @Section("Números y páginas")
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    Callable<Component> issuesAndPages = this::issuesAndPages;

    final NewsletterEditorial editorial;
    final SiteRepository sites;
    final NavigationRepository navigation;
    final NewsletterIssueRepository issues;
    final NewsletterTracking tracking;
    final AudienceCommands audienceCommands;
    final NamedAudiences namedAudiences;
    final Readers readers;
    final java.time.Clock clock;

    public NewsletterForm(NewsletterEditorial editorial, SiteRepository sites, NavigationRepository navigation,
                          NewsletterIssueRepository issues, NewsletterTracking tracking, AudienceCommands audienceCommands,
                          NamedAudiences namedAudiences, Readers readers, java.time.Clock clock) {
        this.editorial = editorial;
        this.sites = sites;
        this.navigation = navigation;
        this.issues = issues;
        this.tracking = tracking;
        this.audienceCommands = audienceCommands;
        this.namedAudiences = namedAudiences;
        this.readers = readers;
        this.clock = clock;
    }

    public NewsletterForm load(String siteId) {
        var site = sites.find(siteId).orElseThrow(() -> new IllegalArgumentException("No existe la newsletter " + siteId));
        var newsletter = editorial.newsletter(siteId);
        var d = site.definition();
        id = site.id();
        name = d.name();
        description = d.description();
        languages = new ArrayList<>(d.languages());
        active = d.active();
        logoMediaId = d.logoMediaId();
        primaryColor = d.theme().primary();
        accentColor = d.theme().accent();
        headerHtml = d.headerHtml();
        footerHtml = d.footerHtml();
        audience(d.audience(), audienceCommands, namedAudiences);
        var language = d.languages().isEmpty() ? "es" : d.languages().getFirst();
        sections = new ArrayList<>(navigation.get(siteId).sections().stream().filter(s -> s.parentId() == null)
                .map(s -> NewsletterSectionForm.of(s.id(), s.name(language),
                        newsletter.settings().sources().getOrDefault(s.id(), List.of()))).toList());
        layout = new ArrayList<>(newsletter.settings().layout().stream().map(b -> BlockForm.of(b, language)).toList());
        var cadence = newsletter.settings().cadence();
        frequency = cadence.frequency().name();
        dayOfWeek = cadence.dayOfWeek() == null ? "MONDAY" : cadence.dayOfWeek().name();
        dayOfMonth = cadence.dayOfMonth() == null ? 1 : cadence.dayOfMonth();
        time = cadence.time().toString();
        autoSend = newsletter.settings().autoSend();
        next = !active ? "Desactivada: no se crean números" : cadence.nextSlotAfter(clock.instant())
                .map(s -> Ui.when(s) + " — " + cadence.describe() + (autoSend ? ", y sale solo" : ", en borrador"))
                .orElse(cadence.describe());
        return this;
    }

    Site.Definition definition() {
        return new Site.Definition(name, description, audience(), languages, logoMediaId,
                new Site.Theme(primaryColor, accentColor), headerHtml, footerHtml, active);
    }

    Newsletter.Settings settings() {
        LocalTime at;
        try {
            at = LocalTime.parse(time == null || time.isBlank() ? "09:00" : time.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("La hora va como HH:mm: «" + time + "»");
        }
        var f = Cadence.Frequency.valueOf(frequency == null ? "NONE" : frequency);
        var cadence = new Cadence(f, f == Cadence.Frequency.WEEKLY ? DayOfWeek.valueOf(dayOfWeek) : null,
                f == Cadence.Frequency.MONTHLY ? dayOfMonth : null, at);
        var language = languages == null || languages.isEmpty() ? "es" : languages.getFirst(); // the blocks' default language
        return new Newsletter.Settings(layout == null ? List.of() : layout.stream().map(b -> b.toBlock(language)).toList(),
                null, cadence, autoSend);
    }

    List<NewsletterEditorial.SectionSpec> specs() {
        return sections == null ? List.of() : sections.stream().map(NewsletterSectionForm::toSpec).toList();
    }

    /** Its issues (number, date, status, opens, clicks) and its pages, each a link to its screen. */
    Component issuesAndPages() {
        if (id == null) {
            return Html.block("");
        }
        var list = issues.ofNewsletter(id);
        var totals = tracking.totals(list.stream().map(i -> i.id()).toList());
        var rows = list.stream().map(i -> {
            var t = totals.getOrDefault(i.id(), NewsletterTracking.Totals.NONE);
            return List.of(Html.link("nº " + i.number(), NewsletterIssuesCrud.route(i.id())), Html.escape(Ui.day(i.sentAt() != null
                            ? i.sentAt() : i.date() == null ? null : i.date().atStartOfDay(Ui.ZONE).toInstant())),
                    Html.escape(i.subject()), Html.escape(i.status().label()
                            + (i.scheduledAt() != null && i.status() == io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue.Status.SCHEDULED
                            ? " · " + Ui.when(i.scheduledAt()) : "")),
                    String.valueOf(t.opens()), String.valueOf(t.clicks()));
        }).toList();
        var pages = editorial.pages(id).stream().map(p -> List.of(
                Html.link(p.draft().in(p.defaultLanguage(), p.defaultLanguage()).title(), VisualEditorPage.formRoute(p.id())),
                Html.escape(p.status().label()), Html.link("Editar sobre la página", VisualEditorPage.route(p.id())))).toList();
        return Html.block(Html.heading("Números")
                + (rows.isEmpty() ? Html.muted("Todavía no tiene números: «Nuevo número» crea el primero.")
                : Html.table(List.of("Número", "Fecha", "Asunto", "Estado", "Aperturas", "Clics"), rows))
                + Html.heading("Páginas de la newsletter (su menú)")
                + (pages.isEmpty() ? Html.muted("Sin páginas: «Nueva página» crea una («Quiénes somos», «Contacto»…).")
                : Html.table(List.of("Página", "Estado", ""), pages))
                + Html.muted("En el portal: " + Ui.PORTAL + "/n/" + id));
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return editorial.create(readers.of(httpRequest), definition(), specs(), settings());
    }

    @Override
    public void save(HttpRequest httpRequest) {
        editorial.configure(readers.of(httpRequest), id, definition(), specs(), settings());
    }

    /** A new issue now, pre-filled from the newsletter: the layout and what was published since the previous one. */
    @Toolbar
    @Action
    @Label("Nuevo número")
    public Object nuevoNumero(HttpRequest httpRequest) {
        var issueId = editorial.newIssue(readers.of(httpRequest), id);
        return UICommand.navigateTo(NewsletterIssuesCrud.route(issueId));
    }

    /** A new page of the newsletter's own, opened in the content form (publish it there; edit it on the page). */
    @Toolbar
    @Action
    @Label("Nueva página")
    public Object nuevaPagina(HttpRequest httpRequest) {
        var pageId = editorial.newPage(readers.of(httpRequest), id, "Nueva página");
        return UICommand.navigateTo(VisualEditorPage.formRoute(pageId));
    }

    /** The newsletter as readers see it, in the portal. */
    @Toolbar
    @Action
    public Object verEnElPortal(HttpRequest httpRequest) {
        return UICommand.navigateTo(Ui.PORTAL + "/n/" + id);
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return List.of("languages", "logoMediaId", "frequency", "dayOfWeek").contains(fieldName)
                || super.supports(fieldType, fieldName, formType);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "languages" -> Catalog.languages();
            case "logoMediaId" -> Catalog.get().media();
            case "frequency" -> List.of(new Option("NONE", "Sin periodicidad (a mano)"), new Option("WEEKLY", "Semanal"),
                    new Option("MONTHLY", "Mensual"));
            case "dayOfWeek" -> Arrays.stream(DayOfWeek.values()).map(d -> new Option(d.name(),
                    d.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")))).toList();
            default -> super.options(fieldName, httpRequest);
        };
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nueva newsletter" : name;
    }
}
