package io.mateu.ecdemo1.contenthub.application.history;

import io.mateu.ecdemo1.contenthub.application.history.ContentDiff.Change;
import io.mateu.ecdemo1.contenthub.application.history.ContentDiff.Kind;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ContentDiffTest {

    static final Function<String, String> NAMES = id -> "Nombre de " + id;

    static ItemContent content(Map<String, Translation> t, List<Block> blocks, Audience audience, Instant publishAt,
                               Instant expireAt) {
        return new ItemContent("noticias", audience, t, blocks, publishAt, expireAt, false, false, null, null);
    }

    static Block html(String id, String title, String html) {
        return new Block(id, BlockType.HTML, null, title, html, null, null, null, null, null);
    }

    static List<Change> diff(ItemContent a, ItemContent b) {
        return ContentDiff.compare(a, b, NAMES, NAMES);
    }

    @Test
    void the_same_content_has_no_differences() {
        var c = content(Map.of("es", new Translation("T", "R", "<p>B</p>")), List.of(html("b1", "Uno", "<p>x</p>")),
                Audience.EVERYONE, null, null);
        assertThat(diff(c, c)).isEmpty();
    }

    @Test
    void a_blocks_text_is_compared_language_by_language() {
        var t = Map.of("es", new Translation("T", "", ""));
        var links = new Block("l1", BlockType.QUICK_LINKS, null, "Enlaces", null, null, null, null,
                List.of(new Block.Link("Buscar", "/buscar")), null);
        var before = content(t, List.of(html("b1", "Hola", "<p>hola</p>")
                .withText("en", "es", new Block.BlockText("Hello", null, null)), links), Audience.EVERYONE, null, null);
        var after = content(t, List.of(html("b1", "Hola", "<p>hola</p>")
                        .withText("en", "es", new Block.BlockText("Hi", "<p>hi</p>", null))
                        .withText("de", "es", new Block.BlockText("Hallo", null, null)),
                links.withText("en", "es", new Block.BlockText(null, null, Map.of("/buscar", "Search")))),
                Audience.EVERYONE, null, null);
        assertThat(diff(before, after)).extracting(Change::field).containsExactly(
                "Bloque «Hola» · título (de)", "Bloque «Hola» · título (en)", "Bloque «Hola» · contenido (en)",
                "Bloque «Enlaces» · textos de los enlaces (en)");
        assertThat(diff(before, after).getLast().after()).isEqualTo("Search → /buscar");
        assertThat(diff(after, after)).isEmpty();
    }

    @Test
    void texts_per_field_and_language() {
        var a = content(Map.of("es", new Translation("Turnos", "", "<p>Lunes</p>")), List.of(), Audience.EVERYONE, null, null);
        var b = content(Map.of("es", new Translation("Turnos de verano", "", "<p>Martes</p>"),
                "en", new Translation("Shifts", "", "")), List.of(), Audience.EVERYONE, null, null);
        var changes = diff(a, b);
        assertThat(changes).extracting(Change::group, Change::field, Change::kind).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Texto (en)", "Idioma", Kind.ADDED),
                org.assertj.core.groups.Tuple.tuple("Texto (en)", "Título", Kind.CHANGED),
                org.assertj.core.groups.Tuple.tuple("Texto (es)", "Título", Kind.CHANGED),
                org.assertj.core.groups.Tuple.tuple("Texto (es)", "Cuerpo", Kind.CHANGED));
        var body = changes.get(3).text();
        assertThat(body).containsExactly(new TextDiff.Segment(TextDiff.Op.REMOVED, "Lunes"),
                new TextDiff.Segment(TextDiff.Op.ADDED, "Martes"));
    }

    @Test
    void a_body_whose_text_stays_but_its_markup_changes_says_so() {
        var a = content(Map.of("es", new Translation("T", "", "<p>Hola</p>")), List.of(), Audience.EVERYONE, null, null);
        var b = content(Map.of("es", new Translation("T", "", "<p><b>Hola</b></p>")), List.of(), Audience.EVERYONE, null, null);
        assertThat(diff(a, b)).singleElement().satisfies(c -> {
            assertThat(c.field()).isEqualTo("Cuerpo");
            assertThat(c.after()).contains("formato");
        });
    }

    @Test
    void blocks_added_removed_moved_and_changed() {
        var t = Map.of("es", new Translation("T", "", ""));
        var a = content(t, List.of(html("b1", "Uno", "<p>a</p>"), html("b2", "Dos", "<p>b</p>"),
                html("b3", "Tres", "<p>c</p>"), html("b4", "Cuatro", "<p>viejo</p>")), Audience.EVERYONE, null, null);
        var b = content(t, List.of(html("b3", "Tres", "<p>c</p>"), html("b1", "Uno", "<p>a</p>"),
                html("b2", "Dos", "<p>b</p>"), html("b5", "Cinco", "<p>nuevo</p>")), Audience.EVERYONE, null, null);
        var changes = diff(a, b);
        assertThat(changes).extracting(Change::field, Change::kind).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("Bloque «Cuatro»", Kind.REMOVED),
                org.assertj.core.groups.Tuple.tuple("Bloque «Cinco»", Kind.ADDED),
                org.assertj.core.groups.Tuple.tuple("Bloque «Tres»", Kind.MOVED));
        var moved = changes.stream().filter(c -> c.kind() == Kind.MOVED).findFirst().orElseThrow();
        assertThat(moved.before()).isEqualTo("posición 3");
        assertThat(moved.after()).isEqualTo("posición 1");

        var c = content(t, List.of(new Block("b1", BlockType.HTML, Audience.in(Attribute.HOTEL, "TFS01"), "Uno",
                "<p>a cambiado</p>", null, null, null, null, null), html("b2", "Dos", "<p>b</p>"),
                html("b3", "Tres", "<p>c</p>"), html("b4", "Cuatro", "<p>viejo</p>")), Audience.EVERYONE, null, null);
        assertThat(diff(a, c)).extracting(Change::field).containsExactly("Bloque «Uno» · contenido", "Bloque «Uno» · audiencia");
        assertThat(diff(a, c).get(0).text()).contains(new TextDiff.Segment(TextDiff.Op.ADDED, " cambiado"));
        assertThat(diff(a, c).get(1).before()).isEqualTo("La de la página");
    }

    @Test
    void a_remote_block_shows_which_ui_it_embeds() {
        var t = Map.of("es", new Translation("T", "", ""));
        var a = content(t, List.of(new Block("r1", BlockType.REMOTE, null, "Socios", null, null, null, null, null, null,
                io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi.of("loyalty", "/members"))), Audience.EVERYONE, null, null);
        var b = content(t, List.of(new Block("r1", BlockType.REMOTE, null, "Socios", null, null, null, null, null, null,
                io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi.of("loyalty", "/tiers"))), Audience.EVERYONE, null, null);
        assertThat(diff(a, b)).singleElement().satisfies(c -> {
            assertThat(c.field()).isEqualTo("Bloque «Socios» · componente remoto");
            assertThat(c.before()).isEqualTo("ui:loyalty/members");
            assertThat(c.after()).isEqualTo("ui:loyalty/tiers");
        });
        assertThat(diff(content(t, List.of(), Audience.EVERYONE, null, null), b)).singleElement()
                .satisfies(c -> assertThat(c.after()).startsWith("Componente remoto: ui:loyalty/tiers"));
    }

    @Test
    void audience_and_publication_window_before_and_after() {
        var t = Map.of("es", new Translation("T", "", ""));
        var publish = Instant.parse("2026-11-10T08:00:00Z");
        var a = content(t, List.of(), Audience.EVERYONE, null, null);
        var b = content(t, List.of(), Audience.allOf(Audience.in(Attribute.DEPARTMENT, "RECEPCION"), new Audience.Named("canarias")),
                publish, Instant.parse("2026-12-01T08:00:00Z"));
        var changes = diff(a, b);
        assertThat(changes).extracting(Change::field, Change::before, Change::after).contains(
                org.assertj.core.groups.Tuple.tuple("Audiencia", "Todos los empleados",
                        "Departamento ∈ {RECEPCION} y «Nombre de canarias»"),
                org.assertj.core.groups.Tuple.tuple("Publicar a partir de", "al publicar", "10/11/2026 09:00"),
                org.assertj.core.groups.Tuple.tuple("Caduca el", "nunca", "01/12/2026 09:00"));
    }
}
