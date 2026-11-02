package io.mateu.ecdemo1.contenthub.application.usecases;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Item bodies are HTML written by editors and shown to everyone: stored only after sanitising, so a
 * script or an event handler never reaches a reader.
 */
public final class HtmlSanitizer {

    static final Safelist SAFE = Safelist.relaxed()
            .addTags("figure", "figcaption", "hr", "span", "section")
            .addAttributes(":all", "class", "style")
            .addAttributes("a", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto", "#");

    private HtmlSanitizer() {
    }

    public static String sanitize(String html) {
        return html == null ? "" : Jsoup.clean(html, SAFE);
    }

    /**
     * Whether two HTML texts say the same, as drawn: sanitised, parsed and written back the same way, white space
     * collapsed — what an editor in a browser gives back of what it was shown, untouched, is the same.
     */
    public static boolean same(String a, String b) {
        return normal(a).equals(normal(b));
    }

    static String normal(String html) {
        var doc = Jsoup.parseBodyFragment(sanitize(html));
        doc.outputSettings().prettyPrint(false);
        return doc.body().html().replace(' ', ' ').replace("&nbsp;", " ").replaceAll("\\s+", " ").replace("> <", "><").trim();
    }

    public static String text(String html) {
        return html == null ? "" : Jsoup.parse(html).text();
    }
}
