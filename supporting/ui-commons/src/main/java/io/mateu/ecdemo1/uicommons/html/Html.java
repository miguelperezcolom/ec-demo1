package io.mateu.ecdemo1.uicommons.html;

import io.mateu.uidl.data.Element;

import java.util.List;
import java.util.Map;

/**
 * Markup for the parts of a screen that are links — above all the "in the chain's other systems"
 * tables. A form field cannot be a link in both renderers — Vaadin draws a link stereotype, Redwood
 * draws its text — while raw markup in an {@link Element} is drawn the same in both. Everything
 * that comes from data is escaped.
 *
 * <p>One Element per screen: Mateu gives every Element the same id, and a second one on the page
 * is drawn into the first one's box.
 */
public final class Html {

    static final String TABLE = "width: 100%; border-collapse: collapse; font-size: .875rem; margin: .25rem 0 1rem;";
    static final String CELL = "text-align: left; padding: .35rem .5rem; border-bottom: 1px solid rgba(128, 128, 128, .25); vertical-align: top;";
    static final String HEAD = CELL + " font-weight: 600; opacity: .75;";
    static final String HEADING = "margin: .75rem 0 .25rem; font-size: 1rem; font-weight: 600;";
    static final String MUTED = "margin: .25rem 0 1rem; opacity: .7; font-size: .875rem;";

    static final String LINKS_TABLE = "width: 100%; border-collapse: collapse; font-size: .875rem;";
    static final String LINKS_CELL = "text-align: left; padding: .3rem .5rem; border-bottom: 1px solid rgba(128, 128, 128, .25); vertical-align: top;";

    /** One line of a {@link #linksTable}: what it is, what to show, and where it goes (or null). */
    public record Link(String what, String label, String href) {
    }

    private Html() {
    }

    /** Markup as a full-width block, the one Element of its screen. */
    public static Element block(String markup) {
        return Element.html("div", Map.of("style", "width: 100%;"), markup);
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        var out = new StringBuilder(text.length());
        for (var c : text.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                // Both renderers interpolate ${…} in an Element's content against the screen's state.
                case '$' -> out.append("&#36;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** A link, or the text alone when there is nowhere to go. External ones open in a new tab. */
    public static String link(String text, String href) {
        if (href == null || href.isBlank()) {
            return escape(text);
        }
        var external = href.startsWith("http://") || href.startsWith("https://");
        return "<a href=\"" + escape(href) + "\"" + (external ? " target=\"_blank\" rel=\"noopener\"" : "") + ">"
                + escape(text) + "</a>";
    }

    public static String heading(String text) {
        return "<h4 style=\"" + HEADING + "\">" + escape(text) + "</h4>";
    }

    public static String muted(String text) {
        return "<p style=\"" + MUTED + "\">" + escape(text) + "</p>";
    }

    /** A table with a header row, whose cells are markup already (escaped, or built with {@link #link}). */
    public static String table(List<String> headers, List<List<String>> rows) {
        var html = new StringBuilder("<table style=\"" + TABLE + "\"><thead><tr>");
        headers.forEach(h -> html.append("<th style=\"").append(HEAD).append("\">").append(escape(h)).append("</th>"));
        html.append("</tr></thead><tbody>");
        for (var row : rows) {
            html.append("<tr>");
            row.forEach(cell -> html.append("<td style=\"").append(CELL).append("\">").append(cell).append("</td>"));
            html.append("</tr>");
        }
        return html.append("</tbody></table>").toString();
    }

    /**
     * Something in the chain's other systems, as a two-column table: what each line is on the
     * left, and its link — or its reference, when that system has no deep link — on the right.
     */
    public static String linksTable(List<Link> links) {
        var html = new StringBuilder("<table style=\"" + LINKS_TABLE + "\"><tbody>");
        for (var link : links) {
            html.append("<tr><th style=\"").append(LINKS_CELL).append(" font-weight: 600; opacity: .75; white-space: nowrap;\">")
                    .append(escape(link.what())).append("</th><td style=\"").append(LINKS_CELL).append("\">")
                    .append(link(link.label(), link.href()))
                    .append("</td></tr>");
        }
        return html.append("</tbody></table>").toString();
    }
}
