package io.mateu.ecdemo1.contenthub.application.history;

import io.mateu.ecdemo1.contenthub.application.history.TextDiff.Op;
import io.mateu.ecdemo1.contenthub.application.history.TextDiff.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextDiffTest {

    @Test
    void html_is_read_as_its_text_one_line_per_paragraph() {
        var text = TextDiff.htmlToText("<h2>Turnos</h2><p>Hola&nbsp;<b>equipo</b> &amp; amigos</p>"
                + "<ul><li>uno</li><li>dos</li></ul><p>línea<br/>dos</p><img src=\"x.png\" alt=\"Mapa\">"
                + "<script>alert(1)</script><p>&#8364; &#x41;</p>");
        assertThat(text).isEqualTo("Turnos\nHola equipo & amigos\n• uno\n• dos\nlínea\ndos\n[imagen: Mapa]\n€ A");
    }

    @Test
    void equal_texts_have_no_change() {
        var diff = TextDiff.diff("igual que antes", "igual que antes");
        assertThat(TextDiff.changed(diff)).isFalse();
        assertThat(diff).containsExactly(new Segment(Op.EQUAL, "igual que antes"));
    }

    @Test
    void a_replaced_phrase_reads_as_one_removal_then_one_addition() {
        var diff = TextDiff.diff("the quick brown fox", "a slow brown fox");
        assertThat(diff).containsExactly(
                new Segment(Op.REMOVED, "the quick"),
                new Segment(Op.ADDED, "a slow"),
                new Segment(Op.EQUAL, " brown fox"));
    }

    @Test
    void words_added_and_removed_in_place() {
        var diff = TextDiff.diff("Abre el lunes a las nueve", "Abre el martes a las nueve y media");
        assertThat(diff).containsExactly(
                new Segment(Op.EQUAL, "Abre el "),
                new Segment(Op.REMOVED, "lunes"),
                new Segment(Op.ADDED, "martes"),
                new Segment(Op.EQUAL, " a las nueve"),
                new Segment(Op.ADDED, " y media"));
    }

    @Test
    void html_bodies_are_compared_by_their_text_never_by_their_tags() {
        var diff = TextDiff.ofHtml("<p>Nuevo <b>horario</b> de verano</p>", "<p>Nuevo <i>horario</i> de invierno</p>");
        assertThat(diff).extracting(Segment::text).noneMatch(t -> t.contains("<"));
        assertThat(diff).containsExactly(
                new Segment(Op.EQUAL, "Nuevo horario de "),
                new Segment(Op.REMOVED, "verano"),
                new Segment(Op.ADDED, "invierno"));
    }

    @Test
    void a_new_paragraph_is_an_addition_on_its_own_line() {
        var diff = TextDiff.ofHtml("<p>Uno</p>", "<p>Uno</p><p>Dos</p>");
        assertThat(diff).containsExactly(new Segment(Op.EQUAL, "Uno"), new Segment(Op.ADDED, "\nDos"));
    }

    @Test
    void only_formatting_changed_is_no_text_change() {
        assertThat(TextDiff.changed(TextDiff.ofHtml("<p>Hola</p>", "<p><strong>Hola</strong></p>"))).isFalse();
    }

    @Test
    void from_or_to_nothing() {
        assertThat(TextDiff.diff("", "Hola")).containsExactly(new Segment(Op.ADDED, "Hola"));
        assertThat(TextDiff.diff("Hola", null)).containsExactly(new Segment(Op.REMOVED, "Hola"));
        assertThat(TextDiff.diff(null, null)).isEqualTo(List.of());
    }
}
