package io.mateu.ecdemo1.contenthub.domain.media;

import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** The media an item's content uses: MEDIA blocks and {@code [[media:id]]} marks in bodies and HTML blocks. */
public final class MediaReferences {

    public static final Pattern MARK = Pattern.compile("\\[\\[media:([A-Za-z0-9_-]+)]]");

    private MediaReferences() {
    }

    public static Set<String> in(ItemContent content) {
        var out = new LinkedHashSet<String>();
        content.translations().values().forEach(t -> collect(t.body(), out));
        for (var b : content.blocks()) {
            if (b.type() == BlockType.MEDIA && b.mediaId() != null) {
                out.add(b.mediaId());
            }
            collect(b.html(), out);
        }
        return out;
    }

    static void collect(String text, Set<String> out) {
        if (text == null) {
            return;
        }
        var m = MARK.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
    }
}
