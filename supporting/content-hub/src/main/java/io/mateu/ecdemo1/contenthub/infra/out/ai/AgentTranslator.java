package io.mateu.ecdemo1.contenthub.infra.out.ai;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.mateu.ecdemo1.contenthub.application.out.Translator;
import io.mateu.ecdemo1.contenthub.application.usecases.HtmlSanitizer;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * {@link Translator} on the IA control plane's {@code intranet-translator}, over A2A with the editor's token:
 * one message per language. The agent's model is whichever the plane gives it, which guarantees no output
 * format, so its reply is read tolerantly (fences, commentary around the JSON) and checked hard: a reply that
 * is not the three texts, or a body that lost markup, a URL or a media mark, is a failure, never a translation.
 * The editor gets a brief message and nothing in the form changes.
 */
@Component
@Slf4j
public class AgentTranslator implements Translator {

    static final String NOT_AVAILABLE = "La IA no está disponible ahora; no se ha cambiado nada";
    static final Pattern MEDIA_MARK = Pattern.compile("\\[\\[media:[^\\]]+\\]\\]");
    /** A model may well put real line breaks inside a JSON string. */
    static final ObjectMapper LENIENT = JsonMapper.builder().enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();

    final A2aClient client;
    final String url;
    final Duration timeout;

    public AgentTranslator(A2aClient client,
                           @Value("${content-hub.translation.agent-url:http://ia-agent:8095/a2a/intranet-translator}") String url,
                           @Value("${content-hub.translation.agent-timeout:PT60S}") Duration timeout) {
        this.client = client;
        this.url = url;
        this.timeout = timeout;
    }

    @Override
    public boolean available() {
        return url != null && !url.isBlank();
    }

    @Override
    public Translation translate(Translation source, String fromLanguage, String toLanguage, String authorization) {
        String reply;
        try {
            reply = client.send(url, message(source, fromLanguage, toLanguage), authorization, timeout);
        } catch (A2aClient.A2aException e) {
            log.info("Translation to {} failed: {}", toLanguage, e.getMessage());
            throw new IllegalStateException(NOT_AVAILABLE);
        }
        return checked(source, reply);
    }

    /** What to translate, as JSON: it carries the HTML without any quoting of ours to undo. */
    static String message(Translation source, String fromLanguage, String toLanguage) {
        var message = new LinkedHashMap<String, String>();
        message.put("from", fromLanguage);
        message.put("to", toLanguage);
        message.put("title", source.title());
        message.put("summary", source.summary() == null ? "" : source.summary());
        message.put("body", source.body() == null ? "" : source.body());
        return Json.write(message);
    }

    static Translation checked(Translation source, String reply) {
        var json = jsonIn(reply).orElseThrow(() -> new IllegalStateException(
                "La IA no ha devuelto una traducción válida; no se ha cambiado nada"));
        var body = HtmlSanitizer.sanitize(json.path("body").asText(""));
        var sourceBody = source.body() == null ? "" : HtmlSanitizer.sanitize(source.body());
        if (!sourceBody.isBlank() && body.isBlank()) {
            throw new IllegalStateException("La IA no ha traducido el cuerpo; no se ha cambiado nada");
        }
        if (!marks(sourceBody).equals(marks(body))) {
            throw new IllegalStateException("La traducción no conserva las marcas de medios [[media:…]]; no se aplica");
        }
        if (!markup(sourceBody).equals(markup(body))) {
            throw new IllegalStateException("La traducción no conserva el formato o los enlaces del cuerpo; no se aplica");
        }
        return new Translation(json.get("title").asText().trim(), json.path("summary").asText(""), body);
    }

    /**
     * The first JSON object in the reply with the shape asked for, whatever surrounds it (markdown fences, a
     * sentence before or after). Empty when there is none.
     */
    static Optional<JsonNode> jsonIn(String reply) {
        if (reply == null) {
            return Optional.empty();
        }
        for (var at = reply.indexOf('{'); at >= 0; at = reply.indexOf('{', at + 1)) {
            try (var parser = LENIENT.createParser(reply.substring(at))) {
                JsonNode node = LENIENT.readTree(parser);
                if (shape(node)) {
                    return Optional.of(node);
                }
            } catch (java.io.IOException e) {
                // not JSON from this brace on: the next one
            }
        }
        return Optional.empty();
    }

    static boolean shape(JsonNode n) {
        return n != null && n.isObject() && n.path("title").isTextual() && !n.path("title").asText().isBlank()
                && (n.path("summary").isMissingNode() || n.path("summary").isNull() || n.path("summary").isTextual())
                && n.path("body").isTextual();
    }

    static List<String> marks(String html) {
        return html == null ? List.of() : MEDIA_MARK.matcher(html).results().map(MatchResult::group).sorted().toList();
    }

    /** The body's structure, regardless of its words: its tags, in order, with their links and sources. */
    static List<String> markup(String html) {
        var out = new ArrayList<String>();
        for (var e : Jsoup.parseBodyFragment(html == null ? "" : html).body().select("*")) {
            var attributes = new ArrayList<String>();
            for (var name : List.of("href", "src")) {
                if (e.hasAttr(name)) {
                    attributes.add(name + "=" + e.attr(name));
                }
            }
            out.add(e.tagName() + attributes);
        }
        return out;
    }

}
