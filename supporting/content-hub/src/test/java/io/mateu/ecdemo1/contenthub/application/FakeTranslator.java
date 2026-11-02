package io.mateu.ecdemo1.contenthub.application;

import io.mateu.ecdemo1.contenthub.application.out.Translator;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A {@link Translator} for tests — never the real AI: it "translates" by putting {@code [to]} before every text
 * (the markup, the links and the {@code [[media:id]]} marks as they are), and records what it was asked. A test
 * may make it misbehave through {@link #body}.
 */
public class FakeTranslator implements Translator {

    public final List<Translation> asked = new ArrayList<>();
    public final List<String> languages = new ArrayList<>();
    /** What it does to the body it returns (after translating it): nothing, by default. */
    public UnaryOperator<String> body = UnaryOperator.identity();

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Translation translate(Translation source, String fromLanguage, String toLanguage, String authorization) {
        asked.add(source);
        languages.add(toLanguage);
        var tag = "[" + toLanguage + "] ";
        var doc = Jsoup.parseBodyFragment(source.body() == null ? "" : source.body());
        doc.outputSettings().prettyPrint(false);
        // every element's text, the body's own too
        doc.body().select("*").forEach(e -> e.textNodes().forEach(n -> prefix(n, tag)));
        return new Translation(tag + source.title(), source.summary().isBlank() ? "" : tag + source.summary(),
                body.apply(doc.body().html()));
    }

    static void prefix(TextNode node, String tag) {
        var text = node.getWholeText();
        if (!text.isBlank() && !text.contains("[[media:")) {
            node.text(tag + text);
        }
    }
}
