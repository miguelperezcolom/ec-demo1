package io.mateu.ecdemo1.contenthub.application.history;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A word diff of two texts, readable by an editor: what was removed and what was added, in order,
 * between the text that stays. An HTML body is first read as its text ({@link #htmlToText}) — what a
 * reader sees, paragraph by paragraph — so a diff never shows tags.
 */
public final class TextDiff {

    public enum Op { EQUAL, ADDED, REMOVED }

    public record Segment(Op op, String text) {
    }

    /** Beyond this many cells the diff is not worth it: the whole middle is shown removed and added. */
    static final long MAX_CELLS = 4_000_000L;

    static final Pattern TOKEN = Pattern.compile("\\n|[^\\S\\n]+|[\\p{L}\\p{N}_]+|[^\\s\\p{L}\\p{N}_]");
    static final Pattern BLOCK_END = Pattern.compile(
            "(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article|figure|figcaption|pre|table|ul|ol)\\s*>");
    static final Pattern BLOCK_START = Pattern.compile(
            "(?i)<(p|div|h[1-6]|blockquote|tr|section|article|figure|pre|table|ul|ol)\\b[^>]*>");
    static final Pattern IMG =Pattern.compile("(?i)<img\\b[^>]*>");
    static final Pattern ALT = Pattern.compile("(?i)\\balt\\s*=\\s*\"([^\"]*)\"");
    static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);");

    private TextDiff() {
    }

    /** Two HTML bodies, compared by their text. */
    public static List<Segment> ofHtml(String before, String after) {
        return diff(htmlToText(before), htmlToText(after));
    }

    /** Whether there is anything but equal text. */
    public static boolean changed(List<Segment> segments) {
        return segments.stream().anyMatch(s -> s.op() != Op.EQUAL);
    }

    /**
     * The text of an HTML fragment: one line per paragraph, heading, list item or line break; a list
     * item with a bullet; an image as «[imagen: alt]»; entities decoded; spaces collapsed.
     */
    public static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        var s = html.replaceAll("(?is)<(script|style)\\b.*?</\\1\\s*>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n");
        s = BLOCK_END.matcher(s).replaceAll("\n");
        s = BLOCK_START.matcher(s).replaceAll("\n");
        s = s.replaceAll("(?i)<li\\b[^>]*>", "• ");
        var img = IMG.matcher(s);
        var withImages = new StringBuilder();
        while (img.find()) {
            var alt = ALT.matcher(img.group());
            var label = alt.find() && !alt.group(1).isBlank() ? "[imagen: " + alt.group(1).trim() + "]" : "[imagen]";
            img.appendReplacement(withImages, Matcher.quoteReplacement(label));
        }
        img.appendTail(withImages);
        s = withImages.toString().replaceAll("<[^>]*>", "");
        s = decode(s);
        var lines = new ArrayList<String>();
        for (var line : s.split("\n", -1)) {
            var clean = line.replace('\u00A0', ' ').replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
            if (!clean.isEmpty()) {
                lines.add(clean);
            }
        }
        return String.join("\n", lines);
    }

    static String decode(String s) {
        var m = ENTITY.matcher(s);
        var out = new StringBuilder();
        while (m.find()) {
            var e = m.group(1);
            String r;
            if (e.startsWith("#x") || e.startsWith("#X")) {
                r = codePoint(e.substring(2), 16, m.group());
            } else if (e.startsWith("#")) {
                r = codePoint(e.substring(1), 10, m.group());
            } else {
                r = switch (e) {
                    case "amp" -> "&";
                    case "lt" -> "<";
                    case "gt" -> ">";
                    case "quot" -> "\"";
                    case "apos" -> "'";
                    case "nbsp" -> " ";
                    case "laquo" -> "«";
                    case "raquo" -> "»";
                    case "euro" -> "€";
                    case "hellip" -> "…";
                    case "mdash" -> "—";
                    case "ndash" -> "–";
                    default -> m.group();
                };
            }
            m.appendReplacement(out, Matcher.quoteReplacement(r));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String codePoint(String digits, int radix, String fallback) {
        try {
            return new String(Character.toChars(Integer.parseInt(digits, radix)));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    static List<String> tokens(String text) {
        var out = new ArrayList<String>();
        var m = TOKEN.matcher(text == null ? "" : text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** A word diff of two plain texts. */
    public static List<Segment> diff(String before, String after) {
        var a = tokens(before);
        var b = tokens(after);
        int prefix = 0;
        while (prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < a.size() - prefix && suffix < b.size() - prefix
                && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix))) {
            suffix++;
        }
        var ops = new ArrayList<Segment>();
        for (int i = 0; i < prefix; i++) {
            ops.add(new Segment(Op.EQUAL, a.get(i)));
        }
        var midA = a.subList(prefix, a.size() - suffix);
        var midB = b.subList(prefix, b.size() - suffix);
        if ((long) midA.size() * midB.size() > MAX_CELLS) {
            midA.forEach(t -> ops.add(new Segment(Op.REMOVED, t)));
            midB.forEach(t -> ops.add(new Segment(Op.ADDED, t)));
        } else {
            ops.addAll(lcs(midA, midB));
        }
        for (int i = a.size() - suffix; i < a.size(); i++) {
            ops.add(new Segment(Op.EQUAL, a.get(i)));
        }
        return tidy(ops);
    }

    /** Token by token, by the longest common subsequence. */
    static List<Segment> lcs(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        var len = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                len[i][j] = a.get(i).equals(b.get(j)) ? len[i + 1][j + 1] + 1 : Math.max(len[i + 1][j], len[i][j + 1]);
            }
        }
        var out = new ArrayList<Segment>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                out.add(new Segment(Op.EQUAL, a.get(i)));
                i++;
                j++;
            } else if (len[i + 1][j] >= len[i][j + 1]) {
                out.add(new Segment(Op.REMOVED, a.get(i++)));
            } else {
                out.add(new Segment(Op.ADDED, b.get(j++)));
            }
        }
        while (i < n) {
            out.add(new Segment(Op.REMOVED, a.get(i++)));
        }
        while (j < m) {
            out.add(new Segment(Op.ADDED, b.get(j++)));
        }
        return out;
    }

    /**
     * Readable runs: a bare space kept between two changes joins them (so "the quick" → "a slow" reads
     * as one replacement, not two), and within each run of changes what was removed goes before what
     * was added, each as one segment.
     */
    static List<Segment> tidy(List<Segment> ops) {
        var marked = new ArrayList<Segment>(ops.size());
        for (int k = 0; k < ops.size(); k++) {
            var s = ops.get(k);
            if (s.op() == Op.EQUAL && s.text().isBlank() && !s.text().contains("\n")
                    && changeAt(ops, k - 1) && changeAt(ops, k + 1)) {
                marked.add(new Segment(Op.REMOVED, s.text()));
                marked.add(new Segment(Op.ADDED, s.text()));
            } else {
                marked.add(s);
            }
        }
        var out = new ArrayList<Segment>();
        var equal = new StringBuilder();
        var removed = new StringBuilder();
        var added = new StringBuilder();
        for (var s : marked) {
            if (s.op() == Op.EQUAL) {
                flush(out, removed, added);
                equal.append(s.text());
            } else {
                if (!equal.isEmpty()) {
                    out.add(new Segment(Op.EQUAL, equal.toString()));
                    equal.setLength(0);
                }
                (s.op() == Op.REMOVED ? removed : added).append(s.text());
            }
        }
        flush(out, removed, added);
        if (!equal.isEmpty()) {
            out.add(new Segment(Op.EQUAL, equal.toString()));
        }
        return out;
    }

    private static boolean changeAt(List<Segment> ops, int k) {
        return k >= 0 && k < ops.size() && ops.get(k).op() != Op.EQUAL;
    }

    private static void flush(List<Segment> out, StringBuilder removed, StringBuilder added) {
        if (!removed.isEmpty()) {
            out.add(new Segment(Op.REMOVED, removed.toString()));
            removed.setLength(0);
        }
        if (!added.isEmpty()) {
            out.add(new Segment(Op.ADDED, added.toString()));
            added.setLength(0);
        }
    }
}
