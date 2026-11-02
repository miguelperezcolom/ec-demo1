package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.Translator;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * «Traducir con IA» for blocks: what a language lacks of each block's text — its title, its HTML, its links'
 * labels — translated from the default language's, in ONE call to the {@link Translator} per language, the
 * same call (and the same checks: the markup, the links, the {@code [[media:id]]} marks kept) as an item's
 * body. The parts travel as the body, one element each — {@code <h3>} a title, {@code <section>} an HTML,
 * {@code <p>} a label — and come back by position; a reply that does not have them all, in order, changes
 * nothing. A draft for the editor to review: nothing is saved from here.
 */
public final class BlockTranslation {

    /** The blocks, with what was translated; {@code parts}: how many texts. */
    public record Result(List<Block> blocks, int parts) {
    }

    enum Kind { TITLE, HTML, LABEL }

    record Part(int block, Kind kind, String url, String text) {
        String element() {
            return switch (kind) {
                case TITLE -> "<h3>" + esc(text) + "</h3>";
                case HTML -> "<section>" + text + "</section>";
                case LABEL -> "<p>" + esc(text) + "</p>";
            };
        }

        String tag() {
            return switch (kind) {
                case TITLE -> "h3";
                case HTML -> "section";
                case LABEL -> "p";
            };
        }
    }

    private BlockTranslation() {
    }

    /**
     * @param blocks          the blocks, their own text in {@code from}
     * @param from            their default language
     * @param to              the language to fill
     * @param context         a title for the translator (the page's or the issue's): it is not used
     * @param authorization   the editor's own {@code Authorization} header: it is who is charged for it
     * @throws RuntimeException with a brief message for the editor when there is no usable translation
     */
    public static Result translate(List<Block> blocks, String from, String to, String context, Translator translator,
                                   String authorization) {
        var parts = missing(blocks, from, to);
        if (parts.isEmpty() || from.equals(to)) {
            return new Result(blocks, 0);
        }
        var body = new StringBuilder();
        parts.forEach(p -> body.append(p.element()));
        var title = context == null || context.isBlank() ? "Bloques" : context;
        var reply = translator.translate(new Translation(title, "", body.toString()), from, to, authorization);
        var doc = Jsoup.parseBodyFragment(reply.body() == null ? "" : reply.body());
        doc.outputSettings().prettyPrint(false);
        var elements = doc.body().children();
        if (elements.size() != parts.size()) {
            throw new IllegalStateException("La IA no ha devuelto todos los textos de los bloques; no se ha cambiado nada");
        }
        var texts = new ArrayList<String>();
        for (int i = 0; i < parts.size(); i++) {
            var e = elements.get(i);
            if (!e.tagName().equals(parts.get(i).tag())) {
                throw new IllegalStateException("La IA ha cambiado la forma de los bloques; no se ha cambiado nada");
            }
            texts.add(parts.get(i).kind() == Kind.HTML ? HtmlSanitizer.sanitize(e.html()) : e.text());
        }
        var out = new ArrayList<>(blocks);
        for (int b = 0; b < blocks.size(); b++) {
            var block = blocks.get(b);
            var before = block.textIn(to, from);
            String newTitle = before.title();
            String newHtml = before.html();
            var labels = new LinkedHashMap<>(before.labels());
            var any = false;
            for (int i = 0; i < parts.size(); i++) {
                var p = parts.get(i);
                if (p.block() != b || texts.get(i).isBlank()) {
                    continue;
                }
                any = true;
                switch (p.kind()) {
                    case TITLE -> newTitle = texts.get(i);
                    case HTML -> newHtml = texts.get(i);
                    case LABEL -> labels.put(p.url(), texts.get(i));
                }
            }
            if (any) {
                out.set(b, block.withText(to, from, new Block.BlockText(newTitle, newHtml, labels)));
            }
        }
        return new Result(List.copyOf(out), parts.size());
    }

    /** What {@code to} lacks of the blocks' text, in order. */
    static List<Part> missing(List<Block> blocks, String from, String to) {
        var out = new ArrayList<Part>();
        for (int b = 0; b < blocks.size(); b++) {
            var block = blocks.get(b);
            var own = block.textIn(from, from);
            var there = block.textIn(to, from);
            if (own.title() != null && there.title() == null) {
                out.add(new Part(b, Kind.TITLE, null, own.title()));
            }
            if (own.html() != null && there.html() == null) {
                out.add(new Part(b, Kind.HTML, null, own.html()));
            }
            final var index = b;
            own.labels().forEach((url, label) -> {
                if (!there.labels().containsKey(url) && !label.equals(url)) { // a bare URL is no text
                    out.add(new Part(index, Kind.LABEL, url, label));
                }
            });
        }
        return out;
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
