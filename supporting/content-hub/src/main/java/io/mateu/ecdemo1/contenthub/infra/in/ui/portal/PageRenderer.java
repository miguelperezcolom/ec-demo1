package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.Card;
import io.mateu.uidl.data.DashboardPanel;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.TextContainer;
import io.mateu.uidl.fluent.Component;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui.esc;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents.column;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents.items;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents.muted;

/**
 * Draws a page's blocks for a reader — the ones the visibility function says they see (page AND
 * block), each filled through the same function — as Mateu components: the tiles of a home (the
 * welcome page's panels) or the sections of a page read as an item (the multimedia content page's
 * cards). Only what editors write in HTML (an HTML or banner block, a medium) stays markup.
 */
@Service
@RequiredArgsConstructor
public class PageRenderer {

    public static final String PORTAL_BASE = "/_intranet";

    final Visibility visibility;
    final ItemRepository items;
    final ContentRenderer renderer;
    final ReaderActions actions;

    /** A page as a reader's home: the welcome banner's overline and subtitle, and its tiles. */
    public record Home(String overline, String subtitle, List<Component> panels) {
    }

    /**
     * A page used as the reader's home. Its banner block, if any, becomes the welcome banner's
     * overline (the block's title) and subtitle (its text); the greeting is the banner's title, so
     * that block draws nothing; every other block is a tile.
     */
    public Home home(Reader reader, VisibleItem page) {
        String overline = null;
        String subtitle = null;
        var panels = new ArrayList<Component>();
        for (var b : visibleBlocks(reader, page.itemId())) {
            if (b.type() == BlockType.GREETING) {
                continue;
            }
            if (b.type() == BlockType.BANNER && overline == null && subtitle == null) {
                overline = blank(b.title()) ? null : b.title();
                subtitle = blank(b.html()) ? null : Jsoup.parse(b.html()).text();
                continue;
            }
            var content = content(reader, b);
            if (content != null) {
                panels.add(panel(b.id(), title(b), content, b.type() == BlockType.HTML || b.type() == BlockType.MEDIA ? 3 : 1));
            }
        }
        return new Home(overline, subtitle, panels);
    }

    /** A page's blocks as the sections of the page read as an item: a card each. */
    public List<Component> sections(Reader reader, String pageId) {
        var out = new ArrayList<Component>();
        for (var b : visibleBlocks(reader, pageId)) {
            var content = b.type() == BlockType.GREETING
                    ? Text.builder().text("Hola, " + reader.name()).container(TextContainer.h2).build()
                    : content(reader, b);
            if (content != null) {
                out.add(section(title(b), content));
            }
        }
        return out;
    }

    /** An item's body, its media expanded, for the reader. */
    public String body(Reader reader, VisibleItem item) {
        return renderer.body(item.body(), PORTAL_BASE, reader.attributes().hash());
    }

    /** The medium or document an item points at, as markup; null when it points at none. */
    public String medium(Reader reader, VisibleItem item) {
        return item.url() != null && item.url().startsWith("media:")
                ? renderer.medium(item.url().substring(6), PORTAL_BASE, reader.attributes().hash()) : null;
    }

    /** A Drive file an item points at by its URL: Google's live preview. */
    public String drive(io.mateu.ecdemo1.contenthub.domain.drive.DriveFileRef ref, String title) {
        return renderer.drive(ref, title);
    }

    public static DashboardPanel panel(String id, String title, Component content, int colSpan) {
        return DashboardPanel.builder().id(id).title(title).content(content).colSpan(colSpan).build();
    }

    public static Card section(String title, Component content) {
        var card = Card.builder().content(content).cssClasses("mateu-section");
        if (!blank(title)) {
            card.title(Text.builder().text(title).container(TextContainer.h3).build());
        }
        return card.build();
    }

    List<Block> visibleBlocks(Reader reader, String pageId) {
        var visible = new HashSet<>(visibility.visibleBlockIds(reader.attributes(), pageId));
        var page = items.find(pageId).flatMap(i -> i.latestVersion()).orElse(null);
        if (page == null) {
            return List.of();
        }
        return page.content().blocks().stream().filter(b -> visible.contains(b.id())).toList();
    }

    /** What a block shows, or null when it shows nothing for this reader. */
    Component content(Reader reader, Block b) {
        var hash = reader.attributes().hash();
        return switch (b.type()) {
            case HTML, BANNER -> blank(b.html()) ? null : html(b.id(), renderer.body(b.html(), PORTAL_BASE, hash));
            case MEDIA -> b.mediaId() == null ? null : html(b.id(), renderer.medium(b.mediaId(), PORTAL_BASE, hash));
            case QUICK_LINKS -> b.links().isEmpty() ? null : column(b.links().stream()
                    .map(l -> (Component) new Anchor(l.label(), l.url(), l.url() != null && l.url().startsWith("/")
                            ? null : "_blank", "", "")).toList());
            case FEATURED -> b.itemId() == null ? null : visibility.item(reader.attributes(), b.itemId())
                    .map(i -> column(blank(i.summary())
                            ? List.of(new Anchor(i.title(), Ui.itemLink(i.itemId())))
                            : List.of(new Anchor(i.title(), Ui.itemLink(i.itemId())), Text.builder().text(i.summary()).build())))
                    .orElse(null);
            case GREETING -> null;
            case PENDING -> {
                if (reader.viewingAs() != null) {
                    yield null;
                }
                var pending = actions.pendingConfirmations(reader);
                yield pending.isEmpty() ? null : items(pending, "");
            }
            case NOTICES -> PortalComponents.withLink(latest(reader, b, Set.of(ItemType.NOTICE), "Sin avisos."),
                    "Todos los avisos", Ui.PORTAL + "/avisos");
            case LATEST -> PortalComponents.withLink(latest(reader, b, Set.of(ItemType.ARTICLE, ItemType.PAGE), "Sin noticias."),
                    "Todas las noticias", Ui.PORTAL + "/noticias");
            case AUTOMATIC_LISTING -> {
                var list = visibility.visibles(reader.attributes(), new ContentQuery(Set.of(), b.sectionId(),
                        null, null, 0, b.limit() == null ? 5 : b.limit())).content();
                yield list.isEmpty() ? null : items(list, "");
            }
        };
    }

    /** A block's heading: its own title, else the one its type stands for. */
    static String title(Block b) {
        if (!blank(b.title())) {
            return b.title();
        }
        return switch (b.type()) {
            case PENDING -> "Pendiente de confirmar";
            case NOTICES -> "Avisos";
            case LATEST -> "Últimas noticias";
            case QUICK_LINKS -> "Enlaces";
            case FEATURED -> "Destacado";
            default -> "";
        };
    }

    /** The latest visible items of some types (and a section, if the block names one). */
    Component latest(Reader reader, Block b, Set<ItemType> types, String empty) {
        var list = visibility.visibles(reader.attributes(), new ContentQuery(types, b.sectionId(), null, null, 0,
                b.limit() == null ? 5 : b.limit())).content();
        return list.isEmpty() ? muted(empty) : items(list, empty);
    }

    /** Markup an editor wrote, in a box of its own (the id keeps two of them apart). */
    static Element html(String id, String markup) {
        return Element.html("div", Map.of("id", "block-" + id, "style", "width: 100%;"), markup);
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** Items as a list of links, for the backoffice's preview (Ver como), which is still markup. */
    public static String list(List<VisibleItem> items) {
        return "<ul style=\"list-style:none;padding:0;margin:0\">" + items.stream()
                .map(i -> "<li style=\"padding:.4rem 0;border-bottom:1px solid rgba(128,128,128,.15)\">"
                        + "<a href=\"" + Ui.itemLink(i.itemId()) + "\"><b>" + esc(i.title()) + "</b></a>"
                        + "<span style=\"opacity:.6;font-size:.8rem\"> · " + Ui.day(i.publishAt()) + "</span>"
                        + (i.summary() == null || i.summary().isBlank() ? ""
                        : "<div style=\"opacity:.75;font-size:.9rem\">" + esc(i.summary()) + "</div>") + "</li>")
                .collect(Collectors.joining()) + "</ul>";
    }
}
