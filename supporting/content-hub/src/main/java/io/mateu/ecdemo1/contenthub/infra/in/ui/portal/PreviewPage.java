package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.PreviewLinks;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLinkStatus;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.render.MediaUrls;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.EmptyState;
import io.mateu.uidl.data.MultimediaContentPage;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer.html;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer.section;

/**
 * A reviewer preview link opened ({@code /intranet/vista-previa/{id}.{signature}}): an item's DRAFT as its
 * readers will see it — title, summary, body, the medium it points at and, for a page, its blocks — marked
 * all over as «Borrador — vista previa», read-only (no likes, comments, favourites or confirmations), and
 * nothing of it recorded as a read. Who opens it is recorded with the link.
 *
 * <p>For anyone logged in to the intranet (the portal needs a login; no reader, nothing shown). With the
 * link made «as» a profile, it reads as that profile: the blocks that profile would not see are left out,
 * and it says when the profile would not see the content at all. Without one, every block is shown, each
 * narrowed one saying who it is for — the reviewer sees the whole page, as the readers between them will.
 */
@Service
@Scope("prototype")
public class PreviewPage implements ComponentTreeSupplier, Identifiable {

    public static final String BANNER = "Borrador — vista previa";

    String id;

    final Readers readers;
    final PreviewLinks previewLinks;
    final AudienceCommands audiences;
    final NamedAudiences namedAudiences;
    final PageRenderer pages;
    final ContentRenderer renderer;

    public PreviewPage(Readers readers, PreviewLinks previewLinks, AudienceCommands audiences, NamedAudiences namedAudiences,
                       PageRenderer pages, ContentRenderer renderer) {
        this.readers = readers;
        this.previewLinks = previewLinks;
        this.audiences = audiences;
        this.namedAudiences = namedAudiences;
        this.pages = pages;
        this.renderer = renderer;
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        if (id == null) {
            id = httpRequest.lastPathItem();
        }
        var reader = readers.real(httpRequest::getHeaderValue);
        PreviewLinks.Opened opened;
        try {
            opened = previewLinks.open(reader, id, PortalSupport.opening(httpRequest));
        } catch (SecurityException e) {
            return unavailable("Inicia sesión", e.getMessage(), null);
        }
        if (opened == null || opened.item() == null) {
            return unavailable("Enlace de vista previa no válido",
                    "Este enlace no existe o está mal copiado. Pide otro a quien te lo envió.", null);
        }
        if (!opened.shown()) {
            return switch (opened.status()) {
                case EXPIRED -> unavailable("Este enlace de vista previa ha caducado",
                        "Caducó el " + Ui.when(opened.link().expiresAt()) + ". Pide uno nuevo a quien te lo envió.", null);
                case PUBLISHED -> unavailable("Este contenido ya está publicado",
                        "La vista previa ya no hace falta: si va dirigido a ti, lo tienes en la intranet.",
                        new Anchor("Abrirlo en la intranet", Ui.itemLink(opened.item().id())));
                default -> unavailable("Este enlace de vista previa se ha revocado",
                        "Quien lo creó lo ha retirado. Pide otro si todavía tienes que revisarlo.", null);
            };
        }
        return preview(reader, opened);
    }

    Component preview(Reader reader, PreviewLinks.Opened opened) {
        var item = opened.item();
        var link = opened.link();
        var draft = item.draft();
        var profile = link.viewAs() == null ? null : audiences.allProfiles().get(link.viewAs());
        var attributes = profile == null ? reader.attributes() : profile.attributes();
        // read as the reviewer, or as the link's profile; never with an editor's permissions
        var as = new Reader(reader.userId(), reader.name(), attributes, Set.of(), profile == null ? null : profile.hash());
        var t = draft.in(attributes.language(), item.defaultLanguage());
        var policy = namedAudiences.policy();
        var labels = namedAudiences.labels();

        var content = new ArrayList<Component>();
        content.add(Notice.builder().theme("warning").text(BANNER + ": todavía no está publicado y puede cambiar. "
                + "Solo lectura. Enlace válido hasta el " + Ui.when(link.expiresAt()) + ".").build());
        if (profile != null) {
            content.add(Notice.builder().theme("info").text("Lo ves como lo verá el perfil «" + link.viewAsLabel() + "».")
                    .build());
            if (!policy.admits(draft.audience(), attributes)) {
                content.add(Notice.builder().theme("error").text("Ese perfil no verá este contenido: no está en su audiencia ("
                        + draft.audience().describe(labels) + ").").build());
            }
        } else {
            content.add(Notice.builder().theme("info").text("Para: " + draft.audience().describe(labels) + ".").build());
        }
        var body = renderer.body(t.body(), PageRenderer.PORTAL_BASE, MediaUrls.EDITOR);
        if (!PageRenderer.blank(body)) {
            content.add(section(null, html("body", body)));
        }
        var url = draft.url();
        if (url != null && url.startsWith("media:")) {
            content.add(section(null, html("medium", renderer.medium(url.substring(6), PageRenderer.PORTAL_BASE, MediaUrls.EDITOR))));
        } else if (url != null && !url.isBlank()) {
            content.add(section(null, new Anchor(item.type() == ItemType.DOCUMENT ? "Abrir el documento" : "Abrir el enlace",
                    url, "_blank", "", "")));
        }
        if (item.type() == ItemType.PAGE) {
            for (var b : draft.blocks()) {
                var effective = Audience.effective(draft.audience(), b.audience());
                if (profile != null && !policy.admits(effective, attributes)) {
                    continue;
                }
                var markup = pages.draftHtml(as, b);
                if (markup.isBlank()) {
                    continue;
                }
                var note = profile == null && !(b.audience() instanceof Audience.Everyone)
                        ? "<p style=\"font-size:.8rem;opacity:.7;margin:.25rem 0 0\">Este bloque solo lo ve: "
                        + Ui.esc(b.audience().describe(labels)) + "</p>" : "";
                content.add(html(b.id(), markup + note));
            }
        }
        return Form.builder()
                .title(t.title())
                .overline(BANNER)
                .subtitle(t.summary())
                .template(new MultimediaContentPage(PortalComponents.TONE.name()))
                .content(content)
                .build();
    }

    static Component unavailable(String title, String description, Component next) {
        var parts = new ArrayList<Component>();
        parts.add(EmptyState.builder().title(title).description(description).build());
        if (next != null) {
            parts.add(next);
        }
        return Form.builder().title(BANNER).content(List.copyOf(parts)).build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return BANNER;
    }
}
