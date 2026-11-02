package io.mateu.ecdemo1.contenthub.application.history;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What changed between two states of an item's content, field by field and language by language, in
 * words an editor reads: texts as word diffs (bodies by their text, not their tags), blocks as added,
 * removed, moved or changed, the audience and the publication window as before → after.
 */
public final class ContentDiff {

    public enum Kind { ADDED, REMOVED, CHANGED, MOVED }

    /**
     * One difference.
     *
     * @param group    where it is: «General», «Texto (es)», «Bloques», «Audiencia», «Publicación»
     * @param field    what: «Título», «Cuerpo», «Bloque «Bienvenida»», «Caduca el»…
     * @param before   the value before, in words (null when the text diff says it all)
     * @param after    the value after, in words
     * @param text     a text's word diff (empty for the rest)
     */
    public record Change(String group, String field, Kind kind, String before, String after,
                         List<TextDiff.Segment> text) {
    }

    public static final String GENERAL = "General";
    public static final String BLOCKS = "Bloques";
    public static final String AUDIENCE = "Audiencia";
    public static final String PUBLICATION = "Publicación";
    static final String FORMAT_ONLY = "Mismo texto; cambia el formato (negritas, enlaces, imágenes…)";
    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    private ContentDiff() {
    }

    /**
     * @param namedLabel   a named audience's name, by id
     * @param sectionLabel a section's name, by id
     */
    public static List<Change> compare(ItemContent before, ItemContent after, Function<String, String> namedLabel,
                                       Function<String, String> sectionLabel) {
        var out = new ArrayList<Change>();
        scalar(out, GENERAL, "Sección", label(before.sectionId(), sectionLabel), label(after.sectionId(), sectionLabel));
        scalar(out, GENERAL, "URL", before.url(), after.url());
        texts(out, before.translations(), after.translations());
        blocks(out, before.blocks(), after.blocks(), namedLabel);
        scalar(out, AUDIENCE, "Audiencia", before.audience().describe(namedLabel), after.audience().describe(namedLabel));
        scalar(out, PUBLICATION, "Publicar a partir de", when(before.publishAt(), "al publicar"), when(after.publishAt(), "al publicar"));
        scalar(out, PUBLICATION, "Caduca el", when(before.expireAt(), "nunca"), when(after.expireAt(), "nunca"));
        scalar(out, PUBLICATION, "Seguimiento de lectura", yesNo(before.readTracking()), yesNo(after.readTracking()));
        scalar(out, PUBLICATION, "Confirmación obligatoria", yesNo(before.confirmationRequired()), yesNo(after.confirmationRequired()));
        scalar(out, PUBLICATION, "Confirmar antes del", when(before.confirmationDeadline(), "sin fecha"),
                when(after.confirmationDeadline(), "sin fecha"));
        scalar(out, PUBLICATION, "Notificar a los lectores", notify(before.notifyReaders()), notify(after.notifyReaders()));
        scalar(out, PUBLICATION, "Importante (también por correo)", yesNo(before.important()), yesNo(after.important()));
        return out;
    }

    static void texts(List<Change> out, Map<String, Translation> before, Map<String, Translation> after) {
        var languages = new TreeSet<String>();
        languages.addAll(before.keySet());
        languages.addAll(after.keySet());
        for (var language : languages) {
            var group = "Texto (" + language + ")";
            var a = before.get(language);
            var b = after.get(language);
            if (a == null) {
                out.add(new Change(group, "Idioma", Kind.ADDED, null, "Traducción nueva", List.of()));
            } else if (b == null) {
                out.add(new Change(group, "Idioma", Kind.REMOVED, "Traducción quitada", null, List.of()));
            }
            var ta = a == null ? new Translation("", "", "") : a;
            var tb = b == null ? new Translation("", "", "") : b;
            text(out, group, "Título", TextDiff.diff(ta.title(), tb.title()), false);
            text(out, group, "Resumen", TextDiff.diff(ta.summary(), tb.summary()), false);
            var body = TextDiff.ofHtml(ta.body(), tb.body());
            text(out, group, "Cuerpo", body, !Objects.equals(ta.body(), tb.body()));
        }
    }

    static void text(List<Change> out, String group, String field, List<TextDiff.Segment> diff, boolean htmlDiffers) {
        if (TextDiff.changed(diff)) {
            out.add(new Change(group, field, Kind.CHANGED, null, null, diff));
        } else if (htmlDiffers) {
            out.add(new Change(group, field, Kind.CHANGED, null, FORMAT_ONLY, List.of()));
        }
    }

    static void blocks(List<Change> out, List<Block> before, List<Block> after, Function<String, String> namedLabel) {
        var a = before.stream().collect(Collectors.toMap(Block::id, b -> b, (x, y) -> x, LinkedHashMap::new));
        var b = after.stream().collect(Collectors.toMap(Block::id, x -> x, (x, y) -> x, LinkedHashMap::new));
        for (var block : before) {
            if (!b.containsKey(block.id())) {
                out.add(new Change(BLOCKS, name(block), Kind.REMOVED, describe(block), null, List.of()));
            }
        }
        for (var block : after) {
            if (!a.containsKey(block.id())) {
                out.add(new Change(BLOCKS, name(block), Kind.ADDED, null,
                        describe(block) + " · en la posición " + (after.indexOf(block) + 1), List.of()));
            }
        }
        // moved: the blocks both keep, out of their relative order (not in the longest run that keeps it)
        var commonBefore = before.stream().map(Block::id).filter(b::containsKey).toList();
        var commonAfter = after.stream().map(Block::id).filter(a::containsKey).toList();
        var staying = new HashSet<String>();
        for (var s : TextDiff.lcs(commonBefore, commonAfter)) {
            if (s.op() == TextDiff.Op.EQUAL) {
                staying.add(s.text());
            }
        }
        for (var block : after) {
            var old = a.get(block.id());
            if (old == null) {
                continue;
            }
            if (!staying.contains(block.id())) {
                out.add(new Change(BLOCKS, name(block), Kind.MOVED, "posición " + (before.indexOf(old) + 1),
                        "posición " + (after.indexOf(block) + 1), List.of()));
            }
            blockChanges(out, old, block, namedLabel);
        }
    }

    static void blockChanges(List<Change> out, Block a, Block b, Function<String, String> namedLabel) {
        var field = name(b);
        if (a.type() != b.type()) {
            out.add(new Change(BLOCKS, field + " · tipo", Kind.CHANGED, a.type().label(), b.type().label(), List.of()));
        }
        if (!Objects.equals(nz(a.title()), nz(b.title()))) {
            out.add(new Change(BLOCKS, field + " · título", Kind.CHANGED, null, null, TextDiff.diff(nz(a.title()), nz(b.title()))));
        }
        if (!Objects.equals(nz(a.html()), nz(b.html()))) {
            var diff = TextDiff.ofHtml(a.html(), b.html());
            out.add(TextDiff.changed(diff) ? new Change(BLOCKS, field + " · contenido", Kind.CHANGED, null, null, diff)
                    : new Change(BLOCKS, field + " · contenido", Kind.CHANGED, null, FORMAT_ONLY, List.of()));
        }
        if (!Objects.equals(a.audience(), b.audience())) {
            out.add(new Change(BLOCKS, field + " · audiencia", Kind.CHANGED, audience(a.audience(), namedLabel),
                    audience(b.audience(), namedLabel), List.of()));
        }
        scalar(out, BLOCKS, field + " · contenido destacado", a.itemId(), b.itemId());
        scalar(out, BLOCKS, field + " · sección listada", a.sectionId(), b.sectionId());
        scalar(out, BLOCKS, field + " · cuántos", a.limit() == null ? null : a.limit().toString(),
                b.limit() == null ? null : b.limit().toString());
        scalar(out, BLOCKS, field + " · enlaces", links(a), links(b));
        scalar(out, BLOCKS, field + " · medio", a.mediaId(), b.mediaId());
        scalar(out, BLOCKS, field + " · componente remoto", remote(a), remote(b));
    }

    /** A REMOTE block's UI as one line: {@code ui:<source><route>?attributes}. */
    static String remote(Block block) {
        return block.remote() == null ? null : block.remote().uri();
    }

    static String audience(Audience audience, Function<String, String> namedLabel) {
        return audience == null || audience instanceof Audience.Everyone ? "La de la página" : audience.describe(namedLabel);
    }

    static String links(Block block) {
        return block.links().isEmpty() ? null
                : block.links().stream().map(l -> l.label() + " → " + l.url()).collect(Collectors.joining("; "));
    }

    static String name(Block block) {
        return "Bloque «" + (block.title() == null || block.title().isBlank()
                ? block.type().label() + " " + block.id() : block.title()) + "»";
    }

    static String describe(Block block) {
        if (block.remote() != null) {
            return block.type().label() + ": " + block.remote().uri();
        }
        var text = TextDiff.htmlToText(block.html());
        return block.type().label() + (text.isEmpty() ? "" : ": " + (text.length() > 80 ? text.substring(0, 80) + "…" : text));
    }

    static void scalar(List<Change> out, String group, String field, String before, String after) {
        var a = blankToNull(before);
        var b = blankToNull(after);
        if (Objects.equals(a, b)) {
            return;
        }
        var kind = a == null ? Kind.ADDED : b == null ? Kind.REMOVED : Kind.CHANGED;
        out.add(new Change(group, field, kind, a == null ? "—" : a, b == null ? "—" : b, List.of()));
    }

    static String label(String id, Function<String, String> labels) {
        return id == null ? null : labels.apply(id);
    }

    static String when(Instant at, String none) {
        return at == null ? none : WHEN.format(at);
    }

    static String yesNo(boolean b) {
        return b ? "Sí" : "No";
    }

    static String notify(Boolean b) {
        return b == null ? "Según el tipo" : b ? "Sí" : "No";
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
