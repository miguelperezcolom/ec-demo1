package io.mateu.ecdemo1.contenthub.infra.out.ai;

import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeTranslatorTest {

    @Test
    void withoutAKeyItIsNotAvailable() {
        assertThat(new ClaudeTranslator("", "claude-opus-5-5").available()).isFalse();
    }

    @Test
    void theRequestAsksForLowEffortTheTranslatedShapeAndServerSideFallback() {
        var params = new ClaudeTranslator("test-key", "claude-opus-5-5")
                .params(new Translation("Hola", "Resumen", "<p>Texto [[media:m1]]</p>"), "es", "en");
        var raw = params;
        var output = raw.outputConfig().orElseThrow();
        assertThat(output.effort().map(Object::toString)).hasValue("low");
        assertThat(output.format()).isPresent();
        assertThat(raw._additionalBodyProperties()).containsKey("fallbacks");
        assertThat(raw.model().asString()).isEqualTo("claude-opus-5-5");
    }

    @Test
    void mediaMarksAreComparedAsASet() {
        assertThat(ClaudeTranslator.marks("<p>[[media:a]] x [[media:b]]</p>"))
                .isEqualTo(ClaudeTranslator.marks("<p>y [[media:b]][[media:a]]</p>"));
        assertThat(ClaudeTranslator.marks("<p>[[media:a]]</p>")).isNotEqualTo(ClaudeTranslator.marks("<p></p>"));
    }
}
