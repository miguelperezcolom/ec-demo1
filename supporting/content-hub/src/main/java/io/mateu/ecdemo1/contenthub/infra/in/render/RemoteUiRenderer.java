package io.mateu.ecdemo1.contenthub.infra.in.render;

import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSourceKind;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.MicroFrontend;
import io.mateu.uidl.fluent.Component;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Draws a use of a registered UI ({@link RemoteUi}) — a remote page's content or a «Componente remoto»
 * block — wherever the intranet draws: the portal (Mateu components), the visual editor (markup) and an
 * e-mail (a link).
 *
 * <ul>
 *   <li><b>MATEU_REMOTE</b>: a {@link MicroFrontend} on the source's gateway prefix and the use's route —
 *   the shells' own federation: the renderer the shell loaded (Vaadin or Redwood) asks the remote for that
 *   route through the gateway, with the reader's token, and draws its UIDL.</li>
 *   <li><b>WEB_COMPONENT</b> and <b>IFRAME</b>: an {@code <ec-ui-source>} element ({@code embed.js}, which
 *   every shell loads, defines it): it loads the source's script with the reader's token and puts the
 *   element in, with the use's attributes; or it opens the allow-listed URL in a sandboxed iframe.</li>
 * </ul>
 *
 * <p>Whether the reader may use the source is the caller's check ({@code RemoteUis.usable}): this only
 * draws.
 */
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class RemoteUiRenderer {

    /** The element embed.js defines for web components and iframes. */
    public static final String ELEMENT = "ec-ui-source";
    /** Public at the gateway on every host that shows the portal; named as the element's import, as the visual editor's. */
    static final String EMBED_SCRIPT = "/_intranet/embed.js";

    final UiSources sources;

    /**
     * How much of the host page an embedded remote may own.
     *
     * <ul>
     *   <li><b>BLOCK</b>: none — it is one block among others. No breadcrumb of its own, and no window title:
     *   the tab keeps the host page's. {@code titled}: the block shows its own title, so the remote's page
     *   title is hidden too (a compact header: only its actions stay).</li>
     *   <li><b>PAGE</b>: a remote page or a hosted menu entry — the remote's title may name the window, but
     *   the breadcrumb is the intranet's (the host page's), not one the remote derives from the host URL.</li>
     * </ul>
     *
     * <p>Mateu has no "embedded / no chrome" option on a {@link MicroFrontend} yet: the island draws its
     * page like a top-level one (an automatic breadcrumb from the HOST's menu and URL, and the remote's
     * SetWindowTitle). Until it has, the island is marked with CSS classes ({@code ec-remote-ui},
     * {@code ec-remote-block|ec-remote-page}, {@code ec-remote-notitle}) and {@code embed.js} takes the chrome
     * off (see there). docs: arquitectura → UIs externas → «Lo que Mateu necesitaría».
     */
    public enum Mode { BLOCK, PAGE }

    /** For the portal, as a block (the host page keeps its chrome), titled by the block. */
    public Component component(UiSource source, RemoteUi use) {
        return component(source, use, Mode.BLOCK, true);
    }

    /** For the portal: the remote screen or element as a Mateu component. */
    public Component component(UiSource source, RemoteUi use, Mode mode) {
        return component(source, use, mode, mode == Mode.BLOCK);
    }

    public Component component(UiSource source, RemoteUi use, Mode mode, boolean titled) {
        var classes = chromeClasses(mode, titled);
        return switch (source.kind()) {
            case MATEU_REMOTE -> MicroFrontend.builder()
                    .baseUrl(source.basePath())
                    .route(use.route() == null ? "" : use.route())
                    .style("display:block;width:100%;")
                    .cssClasses(classes)
                    .build();
            case WEB_COMPONENT, IFRAME -> new Element(ELEMENT, attributes(source, use), Map.of(), "",
                    "display:block;width:100%;", classes, false);
        };
    }

    /** The classes embed.js keys off: {@code ec-remote-ui}, the mode, and whether the remote's title is hidden. */
    public static String chromeClasses(Mode mode, boolean titled) {
        return "ec-remote-ui " + (mode == Mode.BLOCK ? "ec-remote-block" : "ec-remote-page")
                + (mode == Mode.BLOCK && titled ? " ec-remote-notitle" : "");
    }

    /** The {@code <ec-ui-source>} attributes for a web component or an iframe. */
    public static Map<String, String> attributes(UiSource source, RemoteUi use) {
        var out = new LinkedHashMap<String, String>();
        out.put("import", EMBED_SCRIPT);
        out.put("source", source.id());
        out.put("label", source.name());
        if (source.kind() == UiSourceKind.WEB_COMPONENT) {
            out.put("kind", "web-component");
            out.put("src", source.basePath());
            out.put("tag", source.tagName());
            var attrs = new LinkedHashMap<>(use.attributes());
            if (use.route() != null) {
                attrs.putIfAbsent("route", use.route());
            }
            out.put("attrs", json(attrs));
        } else {
            out.put("kind", "iframe");
            out.put("src", source.frameUrl(use));
        }
        return out;
    }

    /**
     * For the visual editor: a card saying what will be there — the editor is a web component of its own,
     * where a live remote would steal the clicks that select blocks. The portal draws it live.
     */
    public String editorHtml(RemoteUi use) {
        var source = sources.find(use.sourceId()).orElse(null);
        if (source == null) {
            return card("🧩 UI externa «" + Html.escape(use.sourceId()) + "» — ya no está registrada: el bloque no se ve en el portal");
        }
        var what = switch (source.kind()) {
            case MATEU_REMOTE -> source.basePath() + (use.route() == null ? "" : use.route());
            case WEB_COMPONENT -> "<" + source.tagName() + (use.attributes().isEmpty() ? "" : " "
                    + use.attributes().entrySet().stream().map(e -> e.getKey() + "=\"" + e.getValue() + "\"")
                    .collect(Collectors.joining(" "))) + ">";
            case IFRAME -> source.frameUrl(use);
        };
        return card("🧩 <b>" + Html.escape(source.name()) + "</b> · " + Html.escape(source.kind().label())
                + (source.active() ? "" : " · <b>inactiva</b>: no se ve en el portal")
                + "<br><code style=\"font-size:.85rem\">" + Html.escape(what) + "</code>"
                + "<br><small style=\"opacity:.75\">Se ve en vivo en el portal, con los permisos de cada lector.</small>");
    }

    /**
     * For an e-mail (the newsletter): a remote UI cannot run in a mail client, so it degrades to a link to
     * the page on the web, where it does. {@code webUrl}: the page in the portal.
     */
    public String fallbackHtml(Block block, String webUrl) {
        return fallbackHtml(label(block), webUrl);
    }

    /** The same, as plain text (the e-mail's text part). */
    public String fallbackText(Block block, String webUrl) {
        return label(block) + " — ver en la web: " + webUrl;
    }

    public static String fallbackHtml(String label, String webUrl) {
        return "<p style=\"margin:1rem 0\"><a href=\"" + Html.escape(webUrl) + "\">" + Html.escape(label)
                + " — ver en la web</a></p>";
    }

    /** What the link says: the block's title, else its source's name. */
    String label(Block block) {
        if (block.title() != null && !block.title().isBlank()) {
            return block.title();
        }
        return block.remote() == null ? "Contenido" : sources.find(block.remote().sourceId()).map(UiSource::name)
                .orElse("Contenido");
    }

    static String card(String inner) {
        return "<div style=\"padding:1rem;border:1px dashed rgba(128,128,128,.6);border-radius:8px\">" + inner + "</div>";
    }

    static String json(Map<String, String> map) {
        return map.entrySet().stream().map(e -> quote(e.getKey()) + ":" + quote(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    static String quote(String s) {
        var out = new StringBuilder("\"");
        for (var c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                case '&' -> out.append("\\u0026");
                default -> {
                    if (c < ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
