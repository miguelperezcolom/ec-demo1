package io.mateu.ecdemo1.contenthub.application.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SnippetsTest {

    @Test
    void a_caption_is_cut_around_the_word_accents_ignored() {
        var text = "palabra ".repeat(60) + "las vacaciones de verano " + "resto ".repeat(60);
        var caption = Snippets.around(text, "VACACIÓN vacaciones");
        assertThat(caption).contains("vacaciones").startsWith("…").endsWith("…");
        assertThat(caption.length()).isLessThanOrEqualTo(Snippets.LENGTH + 2);
    }

    @Test
    void the_passage_head_is_not_the_caption() {
        assertThat(Snippets.ofPassage("Título. Resumen\nEl cuerpo del pasaje", "cuerpo")).isEqualTo("El cuerpo del pasaje");
        assertThat(Snippets.ofPassage("Solo cabecera", "x")).isEqualTo("Solo cabecera");
        assertThat(Snippets.ofPassage(null, "x")).isEmpty();
    }

    @Test
    void a_mediums_mark_is_not_shown() {
        assertThat(Snippets.around("Mira el vídeo: [[media:mvideo0001]] y ya", "video")).isEqualTo("Mira el vídeo: y ya");
    }

    @Test
    void what_reads_as_a_question() {
        assertThat(IntranetSearch.looksLikeQuestion("¿cuántos días de vacaciones tengo?")).isTrue();
        assertThat(IntranetSearch.looksLikeQuestion("Cómo pido un cambio de turno")).isTrue();
        assertThat(IntranetSearch.looksLikeQuestion("how do I request leave")).isTrue();
        assertThat(IntranetSearch.looksLikeQuestion("política de vacaciones")).isFalse();
        assertThat(IntranetSearch.looksLikeQuestion("vacaciones")).isFalse();
        assertThat(IntranetSearch.looksLikeQuestion(" ")).isFalse();
    }
}
