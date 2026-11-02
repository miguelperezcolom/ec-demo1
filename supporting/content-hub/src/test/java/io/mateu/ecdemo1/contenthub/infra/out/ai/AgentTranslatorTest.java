package io.mateu.ecdemo1.contenthub.infra.out.ai;

import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTranslatorTest {

    static final String TOKEN = "Bearer editor-token";
    static final String BODY = "<h2>Hola</h2><p>Texto <b>importante</b> con <a href=\"https://riu.com/a\">enlace</a></p>"
            + "<ul><li>uno</li></ul><p>[[media:m1]]</p>";
    static final String TRANSLATED = "<h2>Hello</h2><p>Some <b>important</b> text with <a href=\"https://riu.com/a\">a link</a></p>"
            + "<ul><li>one</li></ul><p>[[media:m1]]</p>";
    static final Translation SOURCE = new Translation("Hola", "Resumen", BODY);

    FakeA2a a2a;
    AgentTranslator translator;

    @BeforeEach
    void start() throws Exception {
        a2a = new FakeA2a();
        translator = new AgentTranslator(new A2aClient(), a2a.url(), Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        a2a.close();
    }

    static String json() {
        return Json.write(Map.of("title", "Hello", "summary", "Summary", "body", TRANSLATED));
    }

    Translation translate() {
        return translator.translate(SOURCE, "es", "en", TOKEN);
    }

    @Test
    void the_message_carries_the_html_as_it_is_and_the_editors_token_goes_along() throws Exception {
        a2a.response = FakeA2a.reply(json());
        translate();
        assertThat(a2a.tokens).containsExactly(TOKEN);
        var message = Json.MAPPER.readTree(a2a.lastText());
        assertThat(message.path("from").asText()).isEqualTo("es");
        assertThat(message.path("to").asText()).isEqualTo("en");
        assertThat(message.path("title").asText()).isEqualTo("Hola");
        assertThat(message.path("summary").asText()).isEqualTo("Resumen");
        assertThat(message.path("body").asText()).isEqualTo(BODY);
    }

    @Test
    void rich_text_survives_with_its_markup() {
        a2a.response = FakeA2a.reply(json());
        var t = translate();
        assertThat(t.title()).isEqualTo("Hello");
        assertThat(t.summary()).isEqualTo("Summary");
        assertThat(t.body()).contains("<h2>Hello</h2>", "<b>important</b>", "href=\"https://riu.com/a\"", "<li>one</li>", "[[media:m1]]");
    }

    @Test
    void fences_and_commentary_around_the_json_are_tolerated() {
        for (var wrapped : List.of("```json\n" + json() + "\n```", "```\n" + json() + "\n```",
                "Aquí tienes la traducción:\n\n" + json() + "\n\nEspero que te sirva {de ayuda}.",
                "Formato {title, summary, body}:\n" + json())) {
            a2a.response = FakeA2a.reply(wrapped);
            assertThat(translate().title()).as(wrapped).isEqualTo("Hello");
        }
    }

    @Test
    void real_line_breaks_inside_the_strings_are_tolerated() {
        a2a.response = FakeA2a.reply("{\"title\":\"Hello\",\"summary\":\"\",\"body\":\"<h2>Hello</h2>\n<p>Some <b>x</b> "
                + "<a href='https://riu.com/a'>y</a></p><ul><li>one</li></ul><p>[[media:m1]]</p>\"}");
        assertThat(translate().body()).contains("<h2>Hello</h2>");
    }

    @Test
    void an_unparseable_or_misshapen_reply_is_a_failure_and_never_a_translation() {
        for (var bad : List.of("Lo siento, no puedo traducir eso.", "{\"title\": ", "{\"translation\": \"Hello\"}",
                "{\"title\": \"\", \"summary\": \"\", \"body\": \"x\"}", "{\"title\": \"Hello\"}",
                "{\"title\": 3, \"summary\": \"\", \"body\": \"\"}", "[\"Hello\"]")) {
            a2a.response = FakeA2a.reply(bad);
            assertThatThrownBy(this::translate).as(bad).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no se ha cambiado nada");
        }
    }

    @Test
    void a_body_that_lost_its_content_is_refused() {
        a2a.response = FakeA2a.reply(Json.write(Map.of("title", "Hello", "summary", "", "body", "")));
        assertThatThrownBy(this::translate).hasMessageContaining("cuerpo");
    }

    @Test
    void a_lost_or_invented_media_mark_is_refused() {
        a2a.response = FakeA2a.reply(Json.write(Map.of("title", "H", "summary", "", "body", TRANSLATED.replace("[[media:m1]]", ""))));
        assertThatThrownBy(this::translate).hasMessageContaining("marcas de medios");
        a2a.response = FakeA2a.reply(Json.write(Map.of("title", "H", "summary", "", "body", TRANSLATED.replace("m1", "m2"))));
        assertThatThrownBy(this::translate).hasMessageContaining("marcas de medios");
    }

    @Test
    void lost_markup_or_a_changed_link_is_refused() {
        a2a.response = FakeA2a.reply(Json.write(Map.of("title", "H", "summary", "", "body",
                "Hello. Some important text with a link. one. [[media:m1]]")));
        assertThatThrownBy(this::translate).hasMessageContaining("formato");
        a2a.response = FakeA2a.reply(Json.write(Map.of("title", "H", "summary", "", "body",
                TRANSLATED.replace("riu.com/a", "evil.example/a"))));
        assertThatThrownBy(this::translate).hasMessageContaining("formato");
    }

    @Test
    void what_the_body_must_not_carry_is_sanitised_like_any_content() {
        var body = "<script>alert(1)</script><p>Hi</p>";
        a2a.response = FakeA2a.reply(Json.write(Map.of("title", "H", "summary", "", "body", body)));
        var t = translator.translate(new Translation("Hola", "", "<p>Hola</p>"), "es", "en", TOKEN);
        assertThat(t.body()).doesNotContain("script").contains("<p>Hi</p>");
    }

    @Test
    void a_plain_item_without_summary_or_body_translates() {
        a2a.response = FakeA2a.reply("{\"title\":\"Hi\",\"summary\":null,\"body\":\"\"}");
        var t = translator.translate(new Translation("Hola", null, null), "es", "de", TOKEN);
        assertThat(t.title()).isEqualTo("Hi");
        assertThat(t.body()).isEmpty();
    }

    @Test
    void every_agent_failure_is_one_brief_message_with_no_internals() {
        a2a.response = FakeA2a.error(-32052, "Agent 'intranet-translator' is not available: secret detail");
        assertThatThrownBy(this::translate).isInstanceOf(IllegalStateException.class)
                .hasMessage(AgentTranslator.NOT_AVAILABLE);
        a2a.status = 500;
        assertThatThrownBy(this::translate).hasMessage(AgentTranslator.NOT_AVAILABLE);
    }

    @Test
    void without_the_editors_token_nothing_is_sent() {
        assertThatThrownBy(() -> translator.translate(SOURCE, "es", "en", null)).hasMessage(AgentTranslator.NOT_AVAILABLE);
        assertThat(a2a.bodies).isEmpty();
    }

    @Test
    void a_timeout_is_the_same_message() {
        a2a.delayMillis = 2000;
        var hurried = new AgentTranslator(new A2aClient(), a2a.url(), Duration.ofMillis(200));
        assertThatThrownBy(() -> hurried.translate(SOURCE, "es", "en", TOKEN)).hasMessage(AgentTranslator.NOT_AVAILABLE);
    }

    @Test
    void media_marks_are_compared_as_a_set() {
        assertThat(AgentTranslator.marks("<p>[[media:a]] x [[media:b]]</p>"))
                .isEqualTo(AgentTranslator.marks("<p>y [[media:b]][[media:a]]</p>"));
        assertThat(AgentTranslator.marks("<p>[[media:a]]</p>")).isNotEqualTo(AgentTranslator.marks("<p></p>"));
    }

    @Test
    void no_url_is_not_available() {
        assertThat(new AgentTranslator(new A2aClient(), "", Duration.ofSeconds(1)).available()).isFalse();
    }
}
