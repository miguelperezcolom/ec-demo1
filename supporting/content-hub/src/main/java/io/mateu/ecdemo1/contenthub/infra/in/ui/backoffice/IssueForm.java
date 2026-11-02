package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterTracking;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterEditorial;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterRenderer;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Publishers;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * An issue (número) of a newsletter: its subject and date, its blocks — laid out like a page: the
 * intranet's blocks, each able to narrow the audience, and the newsletter's section blocks — and the items
 * of each section. Below, who it goes to (groups of recipients), opens and clicks, and the email as the first
 * group gets it. «Enviar prueba» sends it to you; programming it — sending it — needs content:publish.
 */
@Service
@Scope("prototype")
@Editors
public class IssueForm implements SimpleCrud.CrudForm, OptionsSupplier {

    @Section("Número")
    @ReadOnly
    @HiddenInCreate
    @Label("Id")
    String id;
    @Label("Newsletter")
    @Stereotype(FieldStereotype.select)
    String newsletterId;
    @ReadOnly
    @HiddenInCreate
    @Label("Número")
    String number;
    @ReadOnly
    @HiddenInCreate
    @Label("Estado")
    String state;
    @HiddenInCreate
    @Label("Asunto")
    String subject;
    @HiddenInCreate
    @Label("Fecha")
    LocalDate date;
    @HiddenInCreate
    @Label("Enviar el (vacío: al programar)")
    LocalDateTime scheduledAt;

    @Section("Bloques")
    @HiddenInCreate
    @Label("Bloques (como una página: «Sección de la newsletter» pone los items de una sección)")
    @Colspan(2)
    List<BlockForm> blocks = new ArrayList<>();

    @Section("Items de cada sección")
    @HiddenInCreate
    @Label("Secciones")
    @Colspan(2)
    List<IssueSectionForm> sections = new ArrayList<>();

    @Section("Destinatarios")
    @ReadOnly
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    @Stereotype(FieldStereotype.textarea)
    String groups;

    @Section("Aperturas, clics y vista previa")
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    Callable<Component> report = this::report;

    final NewsletterEditorial editorial;
    final NewsletterService newsletter;
    final SiteRepository sites;
    final NewsletterTracking tracking;
    final AudienceCommands audienceCommands;
    final Readers readers;

    public IssueForm(NewsletterEditorial editorial, NewsletterService newsletter, SiteRepository sites,
                     NewsletterTracking tracking, AudienceCommands audienceCommands, Readers readers) {
        this.editorial = editorial;
        this.newsletter = newsletter;
        this.sites = sites;
        this.tracking = tracking;
        this.audienceCommands = audienceCommands;
        this.readers = readers;
    }

    public IssueForm load(String issueId) {
        var i = editorial.issue(issueId);
        id = i.id();
        newsletterId = i.newsletterId();
        number = "nº " + i.number();
        subject = i.subject();
        date = i.date();
        scheduledAt = Ui.local(i.scheduledAt());
        state = i.status().label() + switch (i.status()) {
            case SCHEDULED -> " para el " + Ui.when(i.scheduledAt());
            case SENT -> " el " + Ui.when(i.sentAt());
            default -> "";
        };
        blocks = new ArrayList<>(i.blocks().stream().map(b -> BlockForm.of(b, language(i.newsletterId()))).toList());
        var names = newsletter.sectionNames(i.newsletterId(), "es");
        var out = new ArrayList<IssueSectionForm>();
        names.forEach((key, name) -> out.add(IssueSectionForm.of(key, i.section(key))));
        i.sections().keySet().stream().filter(k -> !names.containsKey(k)).forEach(k -> out.add(IssueSectionForm.of(k, i.section(k))));
        sections = out;
        var sent = i.sends().stream().collect(Collectors.toMap(s -> NewsletterIssue.groupKey(s.hash(), s.language()), s -> s));
        groups = newsletter.groups(i).stream().map(g -> {
            var s = sent.get(NewsletterIssue.groupKey(g.hash(), g.language()));
            var items = newsletter.itemsFor(i, g.profile(), g.preference()).values().stream().mapToInt(List::size).sum();
            return g.profile().canonical().replace(";", " · ") + " [" + g.language() + "]"
                    + (g.preference().key().isEmpty() ? "" : " (solo " + g.preference().key() + ")") + " — "
                    + g.recipients().size() + " destinatario(s), " + items + " item(s) visibles"
                    + (s == null ? "" : s.error() != null ? " · ERROR: " + s.error()
                    : s.sentAt() == null ? "" : " · enviado " + Ui.when(s.sentAt()));
        }).collect(Collectors.joining("\n"));
        if (groups.isBlank()) {
            groups = "Nadie de la audiencia de la newsletter tiene email o sigue suscrito";
        }
        return this;
    }

    /** Opens and clicks per hotel and department (from each group's profile), never per person; then the email. */
    Component report() {
        if (id == null) {
            return Html.block("");
        }
        var i = editorial.findIssue(id).orElse(null);
        if (i == null) {
            return Html.block("");
        }
        var html = new StringBuilder();
        if (i.sends().isEmpty()) {
            html.append(Html.muted("Sin aperturas ni clics hasta que se envíe."));
        } else {
            var stats = tracking.stats(id);
            var profiles = audienceCommands.allProfiles();
            var byHotel = new java.util.TreeMap<String, long[]>();
            var byDepartment = new java.util.TreeMap<String, long[]>();
            long recipients = 0, opens = 0, clicks = 0;
            for (var send : i.sends()) {
                var key = NewsletterIssue.groupKey(send.hash(), send.language());
                var s = stats.stream().filter(x -> x.group().equals(key)).findFirst().orElse(null);
                long o = s == null ? 0 : s.opens(), c = s == null ? 0 : s.clicks();
                recipients += send.recipients();
                opens += o;
                clicks += c;
                var profile = profiles.get(send.hash().split("~")[0]);
                var attrs = profile == null ? null : profile.attributes();
                add(byHotel, attrs == null ? "?" : attrs.get(Attribute.HOTEL).orElse("Servicios centrales"), send.recipients(), o, c);
                add(byDepartment, attrs == null ? "?" : attrs.get(Attribute.DEPARTMENT).orElse("—"), send.recipients(), o, c);
            }
            html.append("<p><b>").append(opens).append("</b> aperturas y <b>").append(clicks).append("</b> clics de ")
                    .append(recipients).append(" destinatarios. Se cuentan por grupo: el correo de un grupo es uno para todos.</p>")
                    .append(Html.heading("Por hotel")).append(table("Hotel", byHotel))
                    .append(Html.heading("Por departamento")).append(table("Departamento", byDepartment));
        }
        html.append(Html.heading("Vista previa (el correo del primer grupo)"));
        var first = newsletter.groups(i).stream().findFirst();
        html.append(first.map(g -> newsletter.render(i, g.profile(), g.preference(), NewsletterRenderer.Mode.EMAIL,
                "Hola,", previewLinks(i))).orElse("<p>Nadie de la audiencia lo recibiría.</p>"));
        return Html.block(html.toString());
    }

    NewsletterRenderer.Links previewLinks(NewsletterIssue i) {
        var permalink = Ui.PORTAL + "/n/" + i.newsletterId() + "/numeros/" + i.id();
        return new NewsletterRenderer.Links() {
            @Override
            public String item(io.mateu.ecdemo1.contenthub.application.out.VisibleItem item) {
                return Ui.itemLink(item.itemId());
            }

            @Override
            public String rich(String html) {
                return NewsletterRenderer.emailRich(html, permalink);
            }

            @Override
            public String medium(String mediaId) {
                return null;
            }

            @Override
            public String permalink() {
                return permalink;
            }

            @Override
            public String unsubscribe() {
                return Ui.PORTAL + "/n/" + i.newsletterId();
            }

            @Override
            public String logo() {
                return sites.find(i.newsletterId()).map(s -> s.definition().logoMediaId())
                        .map(m -> "/_intranet/n/logo/" + i.newsletterId()).orElse(null);
            }
        };
    }

    static void add(Map<String, long[]> to, String key, long recipients, long opens, long clicks) {
        var v = to.computeIfAbsent(key, k -> new long[3]);
        v[0] += recipients;
        v[1] += opens;
        v[2] += clicks;
    }

    static String table(String what, Map<String, long[]> rows) {
        return Html.table(List.of(what, "Destinatarios", "Aperturas", "Clics"), rows.entrySet().stream()
                .map(r -> List.of(Html.escape(r.getKey()), String.valueOf(r.getValue()[0]), String.valueOf(r.getValue()[1]),
                        String.valueOf(r.getValue()[2]))).toList());
    }

    /** The newsletter's first language: its blocks' default language. */
    String language(String newsletterId) {
        return newsletterId == null ? "es" : sites.find(newsletterId).map(io.mateu.ecdemo1.contenthub.domain.site.Site::defaultLanguage)
                .orElse("es");
    }

    Map<String, List<String>> sectionItems() {
        var out = new LinkedHashMap<String, List<String>>();
        for (var s : sections == null ? List.<IssueSectionForm>of() : sections) {
            if (s != null && s.section != null) {
                out.put(s.section, s.itemIds == null ? List.of() : s.itemIds);
            }
        }
        return out;
    }

    /** «Nuevo» in Números: which newsletter; its next issue opens pre-filled. */
    @Override
    public String create(HttpRequest httpRequest) {
        if (newsletterId == null || newsletterId.isBlank()) {
            throw new IllegalArgumentException("Elige la newsletter");
        }
        return editorial.newIssue(readers.of(httpRequest), newsletterId);
    }

    @Override
    public void save(HttpRequest httpRequest) {
        editorial.editIssue(readers.of(httpRequest), id, subject, date,
                blocks == null ? List.of() : blocks.stream().map(b -> b.toBlock(language(newsletterId))).toList(), sectionItems());
    }

    /** What was published since the issue was opened, added to its sections. */
    @Toolbar
    @Action
    public Object anadirLoNuevo(HttpRequest httpRequest) {
        var added = editorial.addNew(readers.of(httpRequest), id);
        return List.of(load(id), added == 0 ? Message.warning("Nada nuevo desde que se abrió el número")
                : Message.success(added + " item(s) añadidos a sus secciones"));
    }

    /** The issue as you would get it, to your own address (in the demo, to the redirect mailbox). */
    @Toolbar
    @Action
    public Object enviarPrueba(HttpRequest httpRequest) {
        var to = newsletter.sendTest(readers.of(httpRequest), id);
        return Message.success("Prueba enviada a " + to);
    }

    @Toolbar
    @Publishers
    @Action(confirmationRequired = true, confirmationTitle = "¿Programar el envío?",
            confirmationMessage = "Saldrá en la fecha indicada (o ya, si no hay fecha): un correo por grupo de destinatarios.")
    public Object programar(HttpRequest httpRequest) {
        editorial.schedule(readers.of(httpRequest), id, Ui.instant(scheduledAt));
        return List.of(load(id), Message.success("Programado. Sale en menos de un minuto desde su hora."));
    }

    @Toolbar
    @Publishers
    @Action
    public Object volverABorrador(HttpRequest httpRequest) {
        editorial.unschedule(readers.of(httpRequest), id);
        return List.of(load(id), Message.success("De vuelta a borrador: no saldrá hasta que se programe"));
    }

    @Toolbar
    @Publishers
    @Action(confirmationRequired = true, confirmationTitle = "¿Cancelar el número?",
            confirmationMessage = "No saldrá, ni se podrá volver a programar. Su número queda usado.")
    public Object cancelar(HttpRequest httpRequest) {
        editorial.cancel(readers.of(httpRequest), id);
        return List.of(load(id), Message.success("Cancelado"));
    }

    /** The blocks edited on the issue itself, as on a page. */
    @Toolbar
    @Action
    public Object editarSobreElNumero(HttpRequest httpRequest) {
        return UICommand.navigateTo(IssueVisualEditorPage.route(id));
    }

    @Toolbar
    @Action
    public Object verEnElPortal(HttpRequest httpRequest) {
        return UICommand.navigateTo(Ui.PORTAL + "/n/" + newsletterId + "/numeros/" + id);
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "newsletterId".equals(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return sites.findAll().stream().filter(s -> s.kind() == Site.Kind.NEWSLETTER)
                .map(s -> new Option(s.id(), s.name())).toList();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nuevo número" : subject;
    }
}
