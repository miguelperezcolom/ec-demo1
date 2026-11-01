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

    public static String text(String html) {
        return html == null ? "" : Jsoup.parse(html).text();
    }
}
