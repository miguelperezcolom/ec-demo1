package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.media.MediaReferences;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One issue, drawn from its blocks — as the EMAIL (email-safe: tables, inline styles, no scripts, no
 * players: a medium becomes a link to the web version) or as the WEB version (the same blocks, its media
 * playing in place). Both carry the newsletter's identity: its colours, logo, header and footer. What each
 * block shows is decided before (which blocks a group sees, which items): this only draws.
 */
public final class NewsletterRenderer {

    public enum Mode { EMAIL, WEB }

    /** Where things lead, and how media and rich text are drawn — different for the email and the web. */
    public interface Links {
        /** An item's link: the intranet's item page, or the newsletter site's own. */
        String item(VisibleItem item);

        /** Rich text as it is drawn: media marks expanded (web) or turned into a link to the web version (email). */
        String rich(String html);

        /** A medium in place (web); null where it cannot play (email: a link to the web version instead). */
        String medium(String mediaId);

        /** The issue's permanent page, the web version. */
        String permalink();

        /** The newsletter's page, where one unsubscribes. */
        String unsubscribe();

        /** The logo's URL, or null. */
        String logo();

        /** The open pixel's URL, or null. */
        default String pixel() {
            return null;
        }
    }

    /**
     * What a reader (or a group of them) gets of an issue.
     *
     * @param sectionItems per newsletter section, the items of the issue they see and want, in the issue's order
     * @param listings     the items an intranet listing block shows them (latest, notices, automatic listing)
     * @param blockShown   whether they see a block (the newsletter's audience AND the block's)
     * @param sectionNames the newsletter's section names
     * @param greeting     "Hola, Ana" on the web; in an email to a group, just "Hola"
     */
    public record View(Site site, NewsletterIssue issue, Mode mode, String language,
                       Map<String, List<VisibleItem>> sectionItems, Function<Block, List<VisibleItem>> listings,
                       Predicate<Block> blockShown, Map<String, String> sectionNames, String greeting, Links links) {
    }

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"));

    private NewsletterRenderer() {
    }

    /** The whole issue: the identity's header, the blocks, the footer (with the web version and unsubscribe links). */
    public static String render(View v) {
        var d = v.site().definition();
        var theme = d.theme();
        var html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:640px;width:100%;margin:0 auto;"
                + "color:#1f2933;background:#ffffff;line-height:1.5;color-scheme:light\">");
        if (v.mode() == Mode.EMAIL) {
            html.append("<p style=\"font-size:12px;color:#6b7280;text-align:center;margin:4px 0\"><a href=\"")
                    .append(esc(v.links().permalink())).append("\" style=\"color:#6b7280\">")
                    .append(es(v) ? "¿No se ve bien? Ver en el navegador" : "Not displaying well? View it in the browser")
                    .append("</a></p>");
        }
        // header: the identity
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:")
                .append(theme.primary()).append(";color:#ffffff;border-radius:8px 8px 0 0\"><tr><td style=\"padding:16px 20px\">");
        var logo = v.links().logo();
        if (logo != null) {
            html.append("<img src=\"").append(esc(logo)).append("\" alt=\"").append(esc(d.name()))
                    .append("\" style=\"max-height:48px;max-width:200px;display:block;margin-bottom:8px\">");
        }
        html.append("<div style=\"font-size:22px;font-weight:bold\">").append(esc(d.name())).append("</div>")
                .append("<div style=\"font-size:13px;opacity:.9\">").append(es(v) ? "Nº " : "No. ").append(v.issue().number())
                .append(" · ").append(v.issue().date() == null ? "" : DAY.format(v.issue().date())).append("</div>");
        if (!d.headerHtml().isBlank()) {
            html.append("<div style=\"font-size:14px;margin-top:8px\">").append(v.links().rich(d.headerHtml())).append("</div>");
        }
        html.append("</td></tr></table>");
        html.append("<div style=\"padding:8px 20px 16px;border:1px solid #e5e7eb;border-top:0\">");
        html.append("<h1 style=\"font-size:20px;margin:16px 0 8px\">").append(esc(v.issue().subject())).append("</h1>");
        html.append(blocks(v));
        html.append("</div>");
        // footer
        html.append("<div style=\"padding:12px 20px;font-size:12px;color:#6b7280;border:1px solid #e5e7eb;border-top:0;"
                + "border-radius:0 0 8px 8px\">");
        if (!d.footerHtml().isBlank()) {
            html.append("<div>").append(v.links().rich(d.footerHtml())).append("</div>");
        }
        html.append("<p style=\"margin:8px 0 0\">").append(es(v)
                        ? "Recibes «" + esc(d.name()) + "» como empleado: solo incluye lo que puedes ver en la intranet. "
                        : "You receive «" + esc(d.name()) + "» as an employee: it only includes what you can see on the intranet. ")
                .append("<a href=\"").append(esc(v.links().unsubscribe())).append("\" style=\"color:").append(theme.accent())
                .append("\">").append(es(v) ? "Darte de baja de esta newsletter" : "Unsubscribe from this newsletter")
                .append("</a></p></div>");
        var pixel = v.links().pixel();
        if (pixel != null) {
            html.append("<img src=\"").append(esc(pixel)).append("\" width=\"1\" height=\"1\" alt=\"\">");
        }
        return html.append("</div>").toString();
    }

    /** The blocks the view shows, in order; a block with nothing for it draws nothing. */
    public static String blocks(View v) {
        var out = new StringBuilder();
        for (var b : v.issue().blocks()) {
            if (v.blockShown().test(b)) {
                out.append(block(v, b));
            }
        }
        return out.toString();
    }

    static String block(View v, Block b) {
        var accent = v.site().definition().theme().accent();
        var title = b.title() == null ? "" : b.title();
        String inner = switch (b.type()) {
            case HTML -> blank(b.html()) ? null : v.links().rich(b.html());
            case BANNER -> blank(b.html()) ? null : "<div style=\"background:" + v.site().definition().theme().primary()
                    + ";color:#ffffff;padding:16px;border-radius:8px\">" + (title.isBlank() ? ""
                    : "<div style=\"font-weight:bold;font-size:16px\">" + esc(title) + "</div>") + v.links().rich(b.html()) + "</div>";
            case MEDIA -> {
                if (b.mediaId() == null) {
                    yield null;
                }
                var medium = v.links().medium(b.mediaId());
                yield medium != null ? medium : "<p><a href=\"" + esc(v.links().permalink()) + "\" style=\"color:" + accent
                        + "\">" + (es(v) ? "Ver el medio en la versión web" : "See it in the web version") + "</a></p>";
            }
            case QUICK_LINKS -> b.links().isEmpty() ? null : "<ul style=\"padding-left:20px\">" + b.links().stream()
                    .map(l -> "<li><a href=\"" + esc(l.url()) + "\" style=\"color:" + accent + "\">" + esc(l.label())
                            + "</a></li>").reduce("", String::concat) + "</ul>";
            case FEATURED, AUTOMATIC_LISTING, LATEST, NOTICES -> {
                var list = v.listings().apply(b);
                yield list == null || list.isEmpty() ? null : items(v, list);
            }
            case GREETING -> "<p style=\"font-size:16px\">" + esc(v.greeting()) + "</p>";
            case PENDING -> null; // a reader's own pending confirmations: the intranet's home, not a newsletter
            case NEWSLETTER_SECTION -> {
                var key = Newsletter.sectionOf(b);
                var list = v.sectionItems().getOrDefault(key, List.of());
                if (list.isEmpty()) {
                    yield null;
                }
                if (title.isBlank()) {
                    title = v.sectionNames().getOrDefault(key, key);
                }
                yield items(v, list);
            }
            // a block type a newsletter does not draw (one added since: another UI…): nothing in the issue
            default -> null;
        };
        if (inner == null) {
            return "";
        }
        var heading = title.isBlank() || b.type() == BlockType.BANNER || b.type() == BlockType.GREETING ? ""
                : "<h2 style=\"font-size:17px;margin:20px 0 6px;color:" + v.site().definition().theme().primary()
                + ";border-bottom:2px solid " + accent + ";padding-bottom:4px\">" + esc(title) + "</h2>";
        return "<div style=\"margin:12px 0\">" + heading + inner + "</div>";
    }

    static String items(View v, List<VisibleItem> list) {
        var accent = v.site().definition().theme().accent();
        var out = new StringBuilder();
        for (var i : list) {
            out.append("<div style=\"margin:10px 0\"><a href=\"").append(esc(v.links().item(i)))
                    .append("\" style=\"font-weight:bold;font-size:15px;color:").append(accent).append(";text-decoration:none\">")
                    .append(esc(i.title())).append("</a>");
            if (!blank(i.summary())) {
                out.append("<div style=\"font-size:14px;color:#4b5563\">").append(esc(i.summary())).append("</div>");
            }
            out.append("</div>");
        }
        return out.toString();
    }

    /** Rich text for an email: sanitised, its media marks turned into a link to the web version. */
    public static String emailRich(String html, String permalink) {
        var clean = HtmlSanitizer.sanitize(html == null ? "" : html.contains("<") ? html : "<p>" + esc(html) + "</p>");
        return MediaReferences.MARK.matcher(clean).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(
                "<a href=\"" + esc(permalink) + "\">[ver en la versión web]</a>"));
    }

    static boolean es(View v) {
        return v.language() == null || v.language().startsWith("es");
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
