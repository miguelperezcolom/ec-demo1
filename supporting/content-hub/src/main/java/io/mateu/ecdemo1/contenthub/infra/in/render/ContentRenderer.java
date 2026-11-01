package io.mateu.ecdemo1.contenthub.infra.in.render;

import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.domain.media.MediaReferences;
import io.mateu.ecdemo1.uicommons.html.Html;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;

/**
 * Turns stored content into what a page shows: {@code [[media:id]]} marks into the medium — an image, an
 * audio or video player, a PDF viewer, a provider's player — with URLs signed for the reader's profile.
 *
 * <p>The renderers' sanitiser drops iframes, so a PDF or an external video is drawn as a placeholder
 * ({@code data-ec-embed}) that already holds a link (and a thumbnail when there is one), and the shells'
 * {@code embed.js} turns it into the provider's official player — only for the hosts it knows.
 */
@Component
@RequiredArgsConstructor
public class ContentRenderer {

    final MediaRepository media;
    final MediaUrls urls;

    /**
     * An item body or an HTML block, with its media marks expanded. Already sanitised when stored; here
     * only made safe for Mateu's interpolation of {@code ${…}}.
     */
    public String body(String html, String base, String hash) {
        if (html == null) {
            return "";
        }
        var m = MediaReferences.MARK.matcher(html);
        var out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(medium(m.group(1), base, hash)));
        }
        m.appendTail(out);
        return out.toString().replace("${", "&#36;{");
    }

    public String medium(String mediaId, String base, String hash) {
        return media.find(mediaId).map(a -> medium(a, base, hash))
                .orElse("<p style=\"opacity:.6\">[medio no disponible]</p>");
    }

    public String medium(MediaAsset a, String base, String hash) {
        var title = Html.escape(a.title());
        return switch (a.kind()) {
            case IMAGE -> "<figure style=\"margin:1rem 0\"><img src=\"" + src(a, base, hash) + "\" alt=\"" + title
                    + "\" style=\"max-width:100%;border-radius:8px\"/><figcaption style=\"opacity:.7;font-size:.85rem\">"
                    + title + "</figcaption></figure>";
            case AUDIO -> "<figure style=\"margin:1rem 0\"><figcaption>" + title + "</figcaption><audio controls "
                    + "preload=\"metadata\" style=\"width:100%\" src=\"" + src(a, base, hash) + "\"></audio></figure>";
            case VIDEO -> "<figure style=\"margin:1rem 0\"><video controls preload=\"metadata\" "
                    + "style=\"width:100%;max-height:480px;border-radius:8px\" src=\"" + src(a, base, hash)
                    + "\"></video><figcaption style=\"opacity:.7;font-size:.85rem\">" + title + "</figcaption></figure>";
            case PDF -> placeholder(src(a, base, hash), "pdf", "4/3",
                    "<a href=\"" + src(a, base, hash) + "\" target=\"_blank\" rel=\"noopener\">📄 " + title + " (PDF)</a>");
            case EXTERNAL -> external(a, title);
        };
    }

    String external(MediaAsset a, String title) {
        var ext = a.external();
        var href = Html.escape(ext.url());
        var embed = ext.embedUrl();
        if (embed.isEmpty()) {
            return "<p><a href=\"" + href + "\" target=\"_blank\" rel=\"noopener\">🔗 " + title + "</a></p>";
        }
        var inner = ext.thumbnail()
                .map(t -> "<a href=\"" + href + "\" target=\"_blank\" rel=\"noopener\" style=\"display:block;position:relative\">"
                        + "<img src=\"" + Html.escape(t) + "\" alt=\"" + title + "\" style=\"width:100%;border-radius:8px\"/>"
                        + "<span style=\"position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);font-size:3rem\">▶</span></a>")
                .orElse("<a href=\"" + href + "\" target=\"_blank\" rel=\"noopener\">▶ " + title + "</a>");
        return placeholder(embed.get(), "video", "16/9", inner);
    }

    static String placeholder(String embedUrl, String kind, String ratio, String inner) {
        return "<div data-ec-embed=\"" + Html.escape(embedUrl) + "\" data-ec-embed-kind=\"" + kind
                + "\" style=\"margin:1rem 0;aspect-ratio:" + ratio + ";width:100%;max-width:800px\">" + inner + "</div>";
    }

    String src(MediaAsset a, String base, String hash) {
        return Html.escape(urls.url(base, a.id(), hash));
    }
}
