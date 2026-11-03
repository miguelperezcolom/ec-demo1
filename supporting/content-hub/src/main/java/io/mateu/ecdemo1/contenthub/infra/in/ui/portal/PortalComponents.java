package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.drive.DriveDocuments;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.Avatar;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.RouteLink;
import io.mateu.uidl.data.HeroTone;
import io.mateu.uidl.data.HorizontalLayout;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.TextSize;
import io.mateu.uidl.data.VerticalAlignment;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.HttpRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The portal's screens drawn with Mateu's components, not with markup: each renderer paints them its
 * own way — Redwood as Spectra's, inside the page templates (welcome, multimedia content, item
 * overview) the screens fill. Only what editors write (an item's body, an HTML block) stays HTML.
 */
public final class PortalComponents {

    /**
     * Names the browser tab after the page's title. Mateu names it after a page it reflects; a page drawn
     * here (a {@link Form} from {@code component}) left the tab empty, or with the previous page's title.
     */
    public static Component namingTheTab(HttpRequest httpRequest, Component page) {
        if (page instanceof Form form && form.title() != null && !form.title().isBlank()) {
            httpRequest.setAttribute("windowTitle", form.title());
        }
        return page;
    }

    /** The intranet's own colour in the welcome banners, as each console has its own. */
    public static final HeroTone TONE = HeroTone.teal;

    private PortalComponents() {
    }

    /** Items as a list: the title opens the item; the date and the summary under it. */
    public static Component items(List<VisibleItem> items, String empty) {
        if (items.isEmpty()) {
            return muted(empty);
        }
        return column(items.stream().map(PortalComponents::item).toList());
    }

    static Component item(VisibleItem i) {
        var line = Ui.day(i.publishAt());
        if (i.summary() != null && !i.summary().isBlank()) {
            line += " · " + i.summary();
        }
        return VerticalLayout.builder().spacing(false).padding(false)
                .content(List.of(new Anchor(i.title(), Ui.itemLink(i.itemId())), small(line))).build();
    }

    /** What an item is, where it hangs and when it was published: "Noticia · Personas · 02/11/2026". */
    public static String about(VisibleItem i, java.util.Map<String, String> sectionNames) {
        var parts = new ArrayList<String>();
        parts.add(ItemType.valueOf(i.type()).label());
        if (i.sectionId() != null && sectionNames.containsKey(i.sectionId())) {
            parts.add(sectionNames.get(i.sectionId()));
        }
        parts.add(Ui.day(i.publishAt()));
        if (i.expireAt() != null) {
            parts.add("hasta el " + Ui.day(i.expireAt()));
        }
        return String.join(" · ", parts);
    }

    /** Text that is not ours (a passage, the AI's answer): Mateu reads ${…} in a Text as a template. */
    public static String literal(String text) {
        return text == null ? "" : text.replace("${", "$ {");
    }

    /** A Drive file: who last touched it, as far as Drive said, and the ways to it, which open Google's. */
    /**
     * Under Google's preview. The iframe is Google's, inside another site: with third-party cookies blocked it
     * does not see the reader's session and asks them to sign in, and nothing on this side can tell or help.
     */
    static final String DRIVE_PREVIEW_HELP = "¿La vista previa te pide iniciar sesión aunque ya la hayas iniciado? "
            + "Tu navegador no deja usar tu sesión de Google dentro de la intranet: usa «Ver en otra ventana».";

    public static Component driveFile(DriveDocuments.Document d) {
        var links = new ArrayList<Component>();
        // the same preview, but as a page of its own it has the reader's Google session
        links.add(new Anchor("Ver en otra ventana", d.ref().previewUrl(), "_blank", "", ""));
        links.add(new Anchor("Abrir en Drive", d.ref().openUrl(), "_blank", "", ""));
        d.ref().editUrl().ifPresent(edit -> links.add(new Anchor("Editar en Drive", edit, "_blank", "", "")));
        var content = new ArrayList<Component>();
        var line = driveModified(d);
        if (!line.isEmpty()) {
            content.add(small(line));
        }
        content.add(HorizontalLayout.builder().spacing(true).wrap(true).content(links).build());
        content.add(small(DRIVE_PREVIEW_HELP));
        return column(content);
    }

    /** «Modificado por X el …», as far as Drive said; empty when it never did (no Drive configured). */
    public static String driveModified(DriveDocuments.Document d) {
        var m = d.metadata();
        if (m == null || m.modifiedTime() == null) {
            return "";
        }
        return "Modificado" + (m.lastModifiedBy() == null ? "" : " por " + m.lastModifiedBy()) + " el "
                + Ui.when(m.modifiedTime());
    }

    /** A person: their initials, their name opening their card, their job beneath. */
    public static Component person(Employee e, People people) {
        var job = people.jobTitle(e);
        var text = new ArrayList<Component>();
        text.add(new Anchor(People.name(e), People.link(e.userId())));
        if (!job.isEmpty()) {
            text.add(small(job));
        }
        return HorizontalLayout.builder().spacing(true).verticalAlignment(VerticalAlignment.CENTER)
                .content(List.of(avatar(e), VerticalLayout.builder().spacing(false).padding(false)
                        .content(text).build())).build();
    }

    public static Avatar avatar(Employee e) {
        return Avatar.builder().name(People.name(e)).abbreviation(People.initials(People.name(e))).image(e.photoUrl()).build();
    }

    /** People one under the other, or a line saying there is nobody. */
    public static Component people(List<Employee> list, People people, String empty) {
        if (list.isEmpty()) {
            return muted(empty);
        }
        return column(list.stream().map(e -> person(e, people)).collect(Collectors.toList()));
    }

    /** Shown while an editor previews the intranet as some profile (Ver como, in the backoffice). */
    public static List<Component> viewingAs(String profile) {
        return profile == null ? List.of() : List.of(Notice.builder().theme("warning")
                .text("Estás viendo la intranet como el perfil «" + profile + "» (vista previa del backoffice).").build());
    }

    /** A tile's way on: a button to the whole list, as the welcome page's tiles have. */
    public static Button link(String label, String route) {
        return Button.builder().label(label).buttonStyle(ButtonStyle.secondary).actionable(new RouteLink(route)).build();
    }

    /** Some content, and the button that leads on from it. */
    public static Component withLink(Component content, String label, String route) {
        return column(List.of(content, link(label, route)));
    }

    public static Component column(List<Component> content) {
        return VerticalLayout.builder().spacing(true).padding(false).content(content).build();
    }

    public static Text small(String text) {
        return Text.builder().text(text).size(TextSize.s).build();
    }

    public static Text muted(String text) {
        return Text.builder().text(text).size(TextSize.s).style("opacity: .7").build();
    }
}
