package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui.esc;

/**
 * Draws an item for a reader: its body with the media expanded, and — for a page — the blocks the
 * visibility function says they see (page AND block), each filled through the same function.
 */
@Service
@RequiredArgsConstructor
public class PageRenderer {

    public static final String PORTAL_BASE = "/_intranet";

    final Visibility visibility;
    final ItemRepository items;
    final ContentRenderer renderer;

    public String item(Reader reader, VisibleItem item, Map<String, String> sectionNames) {
        var hash = reader.attributes().hash();
        var html = new StringBuilder("<article style=\"max-width:860px\">");
        html.append("<div style=\"opacity:.7;font-size:.85rem;margin-bottom:.5rem\">")
                .append(Ui.chip(ItemType.valueOf(item.type()).label(), "#2f6f8f"))
                .append(item.sectionId() == null ? "" : esc(sectionNames.getOrDefault(item.sectionId(), "")) + " · ")
                .append(Ui.day(item.publishAt()))
                .append(item.expireAt() == null ? "" : " · hasta el " + Ui.day(item.expireAt()))
                .append("</div>");
        if (item.summary() != null && !item.summary().isBlank()) {
            html.append("<p style=\"font-size:1.1rem;opacity:.85\">").append(esc(item.summary())).append("</p>");
        }
        if (item.confirmationRequired()) {
            html.append(Ui.card("⚠️ <b>Este contenido requiere tu confirmación de lectura</b>"
                    + (item.confirmationDeadline() == null ? "" : " antes del " + Ui.day(item.confirmationDeadline()))
                    + ". Usa el botón «Confirmar lectura»."));
        }
        html.append(renderer.body(item.body(), PORTAL_BASE, hash));
        if (item.url() != null && !item.url().isBlank()) {
            if (item.url().startsWith("media:")) {
                html.append(renderer.medium(item.url().substring(6), PORTAL_BASE, hash));
            } else {
                html.append("<p><a href=\"").append(esc(item.url())).append("\" target=\"_blank\" rel=\"noopener\">")
                        .append(item.type().equals("DOCUMENT") ? "📄 Abrir el documento" : "🔗 Abrir el enlace")
                        .append("</a></p>");
            }
        }
        if (ItemType.PAGE.name().equals(item.type())) {
            html.append(blocks(reader, item.itemId()));
        }
        return html.append("</article>").toString();
    }

    String blocks(Reader reader, String pageId) {
        var visible = new HashSet<>(visibility.visibleBlockIds(reader.attributes(), pageId));
        var page = items.find(pageId).flatMap(i -> i.latestVersion()).orElse(null);
        if (page == null) {
            return "";
        }
        var out = new StringBuilder();
        for (var block : page.content().blocks()) {
            if (visible.contains(block.id())) {
                out.append(block(reader, block));
            }
        }
        return out.toString();
    }

    String block(Reader reader, Block b) {
        var hash = reader.attributes().hash();
        var title = b.title() == null || b.title().isBlank() ? "" : "<h3 style=\"margin:.25rem 0 .5rem\">" + esc(b.title()) + "</h3>";
        return switch (b.type()) {
            case HTML -> "<section style=\"margin:1rem 0\">" + title + renderer.body(b.html(), PORTAL_BASE, hash) + "</section>";
            case BANNER -> "<section style=\"margin:1rem 0;padding:1.25rem;border-radius:12px;"
                    + "background:linear-gradient(90deg,#1d5c7a,#2f8f8f);color:#fff\">" + title
                    + renderer.body(b.html(), PORTAL_BASE, hash) + "</section>";
            case MEDIA -> "<section style=\"margin:1rem 0\">" + title
                    + (b.mediaId() == null ? "" : renderer.medium(b.mediaId(), PORTAL_BASE, hash)) + "</section>";
            case QUICK_LINKS -> Ui.card(title + "<ul>" + b.links().stream()
                    .map(l -> "<li><a href=\"" + esc(l.url()) + "\"" + (l.url() != null && l.url().startsWith("/") ? ""
                            : " target=\"_blank\" rel=\"noopener\"") + ">" + esc(l.label()) + "</a></li>")
                    .collect(Collectors.joining()) + "</ul>");
            case FEATURED -> b.itemId() == null ? "" : visibility.item(reader.attributes(), b.itemId())
                    .map(i -> Ui.card(title + "<h2 style=\"margin:.25rem 0\"><a href=\"" + Ui.itemLink(i.itemId()) + "\">"
                            + esc(i.title()) + "</a></h2><p>" + esc(i.summary()) + "</p>"))
                    .orElse("");
            case AUTOMATIC_LISTING -> {
                var list = visibility.visibles(reader.attributes(), new ContentQuery(java.util.Set.of(), b.sectionId(),
                        null, null, 0, b.limit() == null ? 5 : b.limit())).content();
                yield list.isEmpty() ? "" : Ui.card(title + list(list));
            }
        };
    }

    public static String list(java.util.List<VisibleItem> items) {
        return "<ul style=\"list-style:none;padding:0;margin:0\">" + items.stream()
                .map(i -> "<li style=\"padding:.4rem 0;border-bottom:1px solid rgba(128,128,128,.15)\">"
                        + "<a href=\"" + Ui.itemLink(i.itemId()) + "\"><b>" + esc(i.title()) + "</b></a>"
                        + "<span style=\"opacity:.6;font-size:.8rem\"> · " + Ui.day(i.publishAt()) + "</span>"
                        + (i.summary() == null || i.summary().isBlank() ? ""
                        : "<div style=\"opacity:.75;font-size:.9rem\">" + esc(i.summary()) + "</div>") + "</li>")
                .collect(Collectors.joining()) + "</ul>";
    }
}
