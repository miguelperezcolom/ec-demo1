package io.mateu.ecdemo1.contenthub.infra.out.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import io.mateu.ecdemo1.contenthub.application.out.Translator;
import io.mateu.ecdemo1.contenthub.application.usecases.HtmlSanitizer;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@link Translator} on Claude, through the official SDK. Available only with {@code ANTHROPIC_API_KEY}
 * (the cluster's {@code ec-anthropic} secret). One structured-output call per language: the answer is
 * {title, summary, body}; the body is sanitised like any content, and a translation that loses or
 * invents a media mark is refused rather than saved.
 */
@Component
public class ClaudeTranslator implements Translator {

    static final Pattern MEDIA_MARK = Pattern.compile("\\[\\[media:[^\\]]+\\]\\]");
    static final Map<String, String> LANGUAGES = Map.of("es", "Spanish", "en", "English", "de", "German");

    /** What the model answers: the three texts, translated. */
    public record Translated(String title, String summary, String body) {
    }

    final AnthropicClient client;
    final String model;

    public ClaudeTranslator(@Value("${ANTHROPIC_API_KEY:}") String apiKey,
                            @Value("${content-hub.translation.model:claude-opus-5-5}") String model) {
        this.client = apiKey == null || apiKey.isBlank() ? null : AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model;
    }

    @Override
    public boolean available() {
        return client != null;
    }

    @Override
    public Translation translate(Translation source, String fromLanguage, String toLanguage) {
        if (client == null) {
            throw new IllegalStateException("No hay modelo de IA configurado (ANTHROPIC_API_KEY)");
        }
        var response = client.messages().create(params(source, fromLanguage, toLanguage));
        if (response.stopReason().map(r -> r.equals(StopReason.REFUSAL)).orElse(false)) {
            throw new IllegalStateException("El modelo no ha querido traducir este contenido");
        }
        var json = response.content().stream().flatMap(b -> b.text().stream()).findFirst()
                .orElseThrow(() -> new IllegalStateException("El modelo no ha devuelto la traducción")).text();
        var translated = Json.read(json, Translated.class);
        var body = translated.body() == null ? "" : HtmlSanitizer.sanitize(translated.body());
        if (!marks(source.body()).equals(marks(body))) {
            throw new IllegalStateException("La traducción no conserva las marcas de medios [[media:…]]; no se aplica");
        }
        return new Translation(translated.title(), translated.summary(), body);
    }

    /** The request: low effort (a translation needs little reasoning), the answer as {@link Translated}. */
    MessageCreateParams params(Translation source, String fromLanguage, String toLanguage) {
        var from = LANGUAGES.getOrDefault(fromLanguage, fromLanguage);
        var to = LANGUAGES.getOrDefault(toLanguage, toLanguage);
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                // the schema written out, not derived from the record: the SDK's derivation needs a
                // jsonschema-generator older than the one Spring AI brings
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).format(JsonOutputFormat.builder()
                        .schema(JsonOutputFormat.Schema.builder()
                                .putAdditionalProperty("type", JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "title", Map.of("type", "string"),
                                        "summary", Map.of("type", "string"),
                                        "body", Map.of("type", "string"))))
                                .putAdditionalProperty("required", JsonValue.from(java.util.List.of("title", "summary", "body")))
                                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                                .build())
                        .build()).build())
                .system("You translate internal-communication content of a hotel chain's intranet from " + from
                        + " to " + to + ". Keep the meaning and the friendly, professional tone of internal "
                        + "communication. The body is HTML: translate only the text, and keep every tag, attribute "
                        + "and URL exactly as it is. Marks like [[media:abc123]] are placeholders: copy them "
                        + "unchanged, in the same place. Leave a field empty if it is empty in the source.")
                .addUserMessage("Title:\n" + source.title() + "\n\nSummary:\n" + source.summary()
                        + "\n\nBody (HTML):\n" + source.body())
                // refusals fall back server-side to another model instead of ending the request
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
    }

    static java.util.List<String> marks(String html) {
        return html == null ? java.util.List.of()
                : MEDIA_MARK.matcher(html).results().map(java.util.regex.MatchResult::group).sorted().toList();
    }
}
