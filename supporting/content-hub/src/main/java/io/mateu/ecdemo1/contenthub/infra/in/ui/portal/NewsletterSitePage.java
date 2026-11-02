package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterRenderer;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.EmptyState;
import io.mateu.uidl.data.FieldDataType;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.FormLayout;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.fluent.UserTrigger;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A newsletter's site in the portal — a small intranet of its own, in its identity (logo, colours, header
 * and footer) and with its own menu (NewsletterNavigation): its home (the latest issue and the ones before),
 * the archive, each issue's permanent page (the issue as it was mailed, with the reader's visibility), each
 * section (what the issues carried in it, and the site's own articles), and its pages. And the reader's
 * subscription: receive it or not, and which sections.
 *
 * <p>Routes: {@code /intranet/n/{site}}, {@code …/archivo}, {@code …/numeros/{issue}}, {@code …/secciones/{section}}
 * and {@code …/p/{item}} (PortalRoutes). One web component box (an Element) per screen: the whole site is
 * one block of markup, drawn the same by the Vaadin and the Redwood shells, narrow screens included.
 */
@Service
@Scope("prototype")
public class NewsletterSitePage implements ComponentTreeSupplier, Identifiable {

    static final Pattern ROUTE = Pattern.compile(".*/n/([a-z0-9-]+)(?:/(archivo|numeros|secciones|p)(?:/([A-Za-z0-9._-]+))?)?/?(?:\\?.*)?$");

    String path;
    /** The sections the reader wants (the form's field; empty: all). */
    List<String> secciones = new ArrayList<>();

    final PortalSupport portal;
    final NewsletterService newsletters;
    final NewsletterNavigation menu;
    final NavigationRepository navigation;
    final Visibility visibility;
    final ContentRenderer content;
    final PageRenderer pages;

    public NewsletterSitePage(PortalSupport portal, NewsletterService newsletters, NewsletterNavigation menu,
                              NavigationRepository navigation, Visibility visibility, ContentRenderer content,
                              PageRenderer pages) {
        this.portal = portal;
        this.newsletters = newsletters;
        this.menu = menu;
        this.navigation = navigation;
        this.visibility = visibility;
        this.content = content;
        this.pages = pages;
    }

    public NewsletterSitePage load(String path) {
        this.path = path;
        return this;
    }

    /** What the route asks: the site, the view and its subject. */
    record Where(String site, String view, String ref) {
    }

    Where where(HttpRequest httpRequest) {
        if (path == null) {
            path = httpRequest.path();
        }
        var m = ROUTE.matcher(path == null ? "" : path);
        if (!m.matches()) {
            return new Where(null, null, null);
        }
        return new Where(m.group(1), m.group(2) == null ? "home" : m.group(2), m.group(3));
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var w = where(httpRequest);
        var site = w.site() == null ? null : newsletters.readable(reader, w.site()).orElse(null);
        if (site == null) {
            return unavailable("Esta newsletter no existe o no es para ti");
        }
        var preference = newsletters.preference(reader, site.id());
        if (secciones == null || secciones.isEmpty()) {
            secciones = new ArrayList<>(preference.sections());
        }
        String body;
        String current;
        switch (w.view()) {
            case "archivo" -> {
                current = NewsletterNavigation.base(site.id()) + "/archivo";
                body = identity(reader, site) + archive(site, Integer.MAX_VALUE);
            }
            case "numeros" -> {
                current = NewsletterNavigation.base(site.id());
                var issue = w.ref() == null ? null : newsletters.readableIssue(reader, site.id(), w.ref()).orElse(null);
                if (issue == null) {
                    return unavailable("Este número no existe, no ha salido todavía o no es de esta newsletter");
                }
                body = issue(reader, site, issue) + archive(site, 5);
            }
            case "secciones" -> {
                current = NewsletterNavigation.base(site.id()) + "/secciones/" + w.ref();
                body = identity(reader, site) + section(reader, site, w.ref());
            }
            case "p" -> {
                current = NewsletterNavigation.base(site.id()) + "/p/" + w.ref();
                var page = page(reader, site, w.ref());
                if (page == null) {
                    return unavailable("Esta página no existe o no la puedes ver");
                }
                body = identity(reader, site) + page;
            }
            default -> {
                current = NewsletterNavigation.base(site.id());
                var latest = newsletters.latestSent(site.id()).orElse(null);
                body = latest == null ? identity(reader, site)
                        + "<p style=\"opacity:.75\">Todavía no ha salido ningún número.</p>"
                        : issue(reader, site, latest) + archive(site, 5);
            }
        }
        var out = new ArrayList<Component>(PortalComponents.viewingAs(reader.viewingAs()));
        // the shell's theme, light or dark: the page's own text and background; the newsletter's colours only
        // on its bands (with ink that reads on them) and mixed with the text colour for links and tabs
        out.add(Html.block("<div style=\"max-width:760px;margin:0 auto;width:100%;box-sizing:border-box;color:inherit\">"
                + nav(httpRequest, site, current) + body + "</div>"));
        out.addAll(subscription(reader, site, preference));
        var toolbar = new ArrayList<UserTrigger>();
        if (reader.viewingAs() == null) {
            toolbar.add(preference.subscribed()
                    ? Button.builder().label("Darme de baja").actionId("baja").buttonStyle(ButtonStyle.secondary).build()
                    : Button.builder().label("Suscribirme").actionId("alta").buttonStyle(ButtonStyle.primary).build());
        }
        return Form.builder().title(site.name()).subtitle(site.definition().description())
                .overline(preference.subscribed() ? "Newsletter · la recibes" : "Newsletter · de baja")
                .toolbar(toolbar).content(out).build();
    }

    /** The menu: the seam (NewsletterNavigation), drawn as a bar that wraps on a narrow screen. */
    String nav(HttpRequest httpRequest, Site site, String current) {
        var theme = site.definition().theme();
        var html = new StringBuilder("<nav style=\"display:flex;flex-wrap:wrap;gap:.25rem .5rem;margin:0 0 1rem;"
                + "padding:.5rem 0;border-bottom:2px solid " + theme.accent() + "\">");
        for (var e : menu.menu(httpRequest, site)) {
            if (e.href() == null) { // a submenu: its label, then its entries
                html.append("<span style=\"padding:.25rem .3rem;font-size:.8rem;opacity:.65;align-self:center\">")
                        .append(Html.escape(e.label())).append("</span>");
                continue;
            }
            var on = e.href().equals(current);
            html.append("<a href=\"").append(Html.escape(e.href())).append("\" style=\"padding:.25rem .6rem;border-radius:999px;"
                    + "text-decoration:none;font-size:.9rem;").append(on ? "background:" + theme.primary() + ";color:" + NewsletterRenderer.ink(theme.primary())
                    : "color:color-mix(in srgb, " + theme.primary() + " 60%, currentColor)").append("\">").append(Html.escape(e.label())).append("</a>");
        }
        return html.append("</nav>").toString();
    }

    /** The identity's header, for the views that are not an issue (an issue draws its own). */
    String identity(Reader reader, Site site) {
        var d = site.definition();
        var logo = d.logoMediaId() == null ? "" : "<img src=\"/_intranet/n/logo/" + Html.escape(site.id())
                + "\" alt=\"\" style=\"max-height:40px;max-width:160px;display:block;margin-bottom:.5rem\">";
        return "<div style=\"background:" + d.theme().primary() + ";color:" + NewsletterRenderer.ink(d.theme().primary()) + ";padding:1rem 1.25rem;border-radius:8px;margin-bottom:1rem\">"
                + logo + "<div style=\"font-size:1.3rem;font-weight:bold\">" + Html.escape(d.name()) + "</div>"
                + (d.headerHtml().isBlank() ? "" : "<div style=\"font-size:.9rem\">" + rich(reader, d.headerHtml()) + "</div>")
                + "</div>";
    }

    String rich(Reader reader, String html) {
        return content.body(html, PageRenderer.PORTAL_BASE, reader.attributes().hash());
    }

    /** An issue as the reader gets it: the email's blocks, with the media playing and the reader's name. */
    String issue(Reader reader, Site site, NewsletterIssue issue) {
        var hash = reader.attributes().hash();
        var preference = newsletters.preference(reader, site.id());
        var links = new NewsletterRenderer.Links() {
            @Override
            public String item(VisibleItem item) {
                return Ui.itemLink(item.itemId());
            }

            @Override
            public String rich(String html) {
                return content.body(html, PageRenderer.PORTAL_BASE, hash);
            }

            @Override
            public String medium(String mediaId) {
                return content.medium(mediaId, PageRenderer.PORTAL_BASE, hash);
            }

            @Override
            public String permalink() {
                return NewsletterNavigation.base(site.id()) + "/numeros/" + issue.id();
            }

            @Override
            public String unsubscribe() {
                return NewsletterNavigation.base(site.id());
            }

            @Override
            public String logo() {
                return site.definition().logoMediaId() == null ? null : "/_intranet/n/logo/" + site.id();
            }
        };
        return newsletters.render(issue, reader.attributes(), preference.subscribed() ? preference : null,
                NewsletterRenderer.Mode.WEB, "Hola, " + reader.name(), new SiteLinks(links, site.id(), newsletters));
    }

    /** The sent issues, the latest first, each a link to its page. */
    String archive(Site site, int max) {
        var list = newsletters.archive(site.id());
        if (list.isEmpty()) {
            return "";
        }
        var html = new StringBuilder("<h3 style=\"margin:1.5rem 0 .5rem\">Números anteriores</h3><ul style=\"list-style:none;padding:0\">");
        list.stream().limit(max).forEach(i -> html.append("<li style=\"padding:.35rem 0;border-bottom:1px solid rgba(128,128,128,.2)\">")
                .append("<a href=\"").append(Html.escape(NewsletterNavigation.base(site.id()) + "/numeros/" + i.id())).append("\">")
                .append("nº ").append(i.number()).append(" · ").append(Html.escape(i.subject())).append("</a>")
                .append("<span style=\"opacity:.6;font-size:.85rem\"> · ").append(Ui.day(i.sentAt())).append("</span></li>"));
        if (list.size() > max) {
            html.append("<li style=\"padding:.35rem 0\"><a href=\"").append(NewsletterNavigation.base(site.id()))
                    .append("/archivo\">Todos los números</a></li>");
        }
        return html.append("</ul>").toString();
    }

    /** A section: the site's own articles in it, and what the sent issues carried in it — what the reader sees. */
    String section(Reader reader, Site site, String sectionId) {
        var language = site.languageFor(reader.attributes().language());
        var name = navigation.get(site.id()).find(sectionId).map(s -> s.name(language)).orElse(sectionId);
        var ids = new LinkedHashSet<String>();
        visibility.visibles(reader.attributes(), new ContentQuery(Set.of(), sectionId, null, null, 0, 50).in(site.id()))
                .content().stream().filter(i -> !ItemType.PAGE.name().equals(i.type())).forEach(i -> ids.add(i.itemId()));
        newsletters.archive(site.id()).forEach(i -> ids.addAll(i.section(sectionId)));
        var visible = new LinkedHashMap<String, VisibleItem>();
        if (!ids.isEmpty()) {
            visibility.visibles(reader.attributes(), ContentQuery.ids(List.copyOf(ids))).content().forEach(i -> visible.put(i.itemId(), i));
        }
        var html = new StringBuilder("<h2 style=\"margin:.5rem 0\">").append(Html.escape(name)).append("</h2>");
        var shown = ids.stream().map(visible::get).filter(java.util.Objects::nonNull).toList();
        if (shown.isEmpty()) {
            return html.append("<p style=\"opacity:.75\">Nada que puedas ver en esta sección todavía.</p>").toString();
        }
        shown.forEach(i -> html.append("<div style=\"margin:.75rem 0\"><a href=\"")
                .append(Html.escape(NewsletterService.itemPath(Ui.PORTAL, i, site.id(), id -> isOwn(site, id))))
                .append("\" style=\"font-weight:bold;color:color-mix(in srgb, ").append(site.definition().theme().accent())
                .append(" 60%, currentColor)\">")
                .append(Html.escape(i.title())).append("</a>").append(i.summary() == null || i.summary().isBlank() ? ""
                        : "<div style=\"opacity:.8\">" + Html.escape(i.summary()) + "</div>").append("</div>"));
        return html.toString();
    }

    boolean isOwn(Site site, String itemId) {
        return newsletters.isOwnItem(site.id(), itemId);
    }

    /** One of the site's own pages or articles: its body and its blocks, in the site's identity. */
    String page(Reader reader, Site site, String itemId) {
        if (itemId == null || !isOwn(site, itemId)) {
            return null;
        }
        var item = visibility.item(reader.attributes(), itemId).orElse(null);
        if (item == null) {
            return null;
        }
        var html = new StringBuilder("<h2 style=\"margin:.5rem 0\">").append(Html.escape(item.title())).append("</h2>");
        if (item.summary() != null && !item.summary().isBlank()) {
            html.append("<p style=\"opacity:.8\">").append(Html.escape(item.summary())).append("</p>");
        }
        html.append(pages.body(reader, item));
        var hash = reader.attributes().hash();
        for (var b : pages.visibleBlocks(reader, itemId)) {
            html.append(pages.blockHtml(reader, b, hash));
        }
        return html.toString();
    }

    /** Receive it or not, and which sections — only those of the newsletter. */
    List<Component> subscription(Reader reader, Site site, io.mateu.ecdemo1.contenthub.application.out.NewsletterPreferences.Preference preference) {
        if (reader.viewingAs() != null) {
            return List.of();
        }
        if (!preference.subscribed()) {
            return List.of(Notice.builder().text("No la recibes por correo. Puedes leerla aquí igualmente, o «Suscribirme».").build());
        }
        var language = site.languageFor(reader.attributes().language());
        var options = navigation.get(site.id()).sections().stream().filter(s -> s.parentId() == null)
                .map(s -> new Option(s.id(), s.name(language))).toList();
        if (options.size() < 2) {
            return List.of();
        }
        return List.of(PageRenderer.section("Qué secciones recibo", PortalComponents.column(List.of(
                FormLayout.builder().maxColumns(1).content(List.of(FormField.builder().id("secciones")
                        .label("Solo estas secciones (ninguna marcada: todas)").dataType(FieldDataType.array)
                        .stereotype(FieldStereotype.checkbox).options(options).build())).build(),
                Button.builder().label("Guardar mis secciones").actionId("guardarSecciones").buttonStyle(ButtonStyle.secondary).build()))));
    }

    @Action
    public Object baja(HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var site = where(httpRequest).site();
        newsletters.subscribe(reader, site, false, Set.of());
        return List.of(this, Message.success("Te has dado de baja: ya no te llegará por correo"));
    }

    @Action
    public Object alta(HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var site = where(httpRequest).site();
        newsletters.subscribe(reader, site, true, Set.of());
        return List.of(this, Message.success("Suscrito: te llegará cada número"));
    }

    @Action
    @SuppressWarnings("unchecked")
    public Object guardarSecciones(HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var site = where(httpRequest).site();
        var state = httpRequest.runActionRq() == null ? null : httpRequest.runActionRq().componentState();
        var chosen = new java.util.HashSet<String>();
        if (state != null && state.get("secciones") instanceof List<?> l) {
            l.forEach(o -> chosen.add(String.valueOf(o)));
        }
        newsletters.subscribe(reader, site, true, chosen);
        secciones = new ArrayList<>(chosen);
        return List.of(this, Message.success(chosen.isEmpty() ? "Recibirás todas las secciones"
                : "Recibirás " + chosen.size() + " sección(es)"));
    }

    static Component unavailable(String title) {
        return Form.builder().title("No disponible").content(List.of(EmptyState.builder().title(title)
                .description("Mira en «Newsletters» las que son para ti.").build())).build();
    }

    @Override
    public String id() {
        return path;
    }

    @Override
    public String toString() {
        return "Newsletter";
    }

    /** The web links, with the site's own items under the site. */
    record SiteLinks(NewsletterRenderer.Links inner, String site, NewsletterService newsletters) implements NewsletterRenderer.Links {
        @Override
        public String item(VisibleItem item) {
            return NewsletterService.itemPath(Ui.PORTAL, item, site, id -> newsletters.isOwnItem(site, id));
        }

        @Override
        public String rich(String html) {
            return inner.rich(html);
        }

        @Override
        public String medium(String mediaId) {
            return inner.medium(mediaId);
        }

        @Override
        public String permalink() {
            return inner.permalink();
        }

        @Override
        public String unsubscribe() {
            return inner.unsubscribe();
        }

        @Override
        public String logo() {
            return inner.logo();
        }
    }
}
