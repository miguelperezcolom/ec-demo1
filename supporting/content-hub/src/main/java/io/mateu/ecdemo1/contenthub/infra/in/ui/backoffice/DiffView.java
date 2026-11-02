package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.history.ContentDiff;
import io.mateu.ecdemo1.contenthub.application.history.ItemHistory;
import io.mateu.ecdemo1.contenthub.application.history.TextDiff;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/** The history's markup: the steps as a table, a comparison as a card per group of fields. */
final class DiffView {

    static final String INS = "background:rgba(22,163,74,.22);text-decoration:none;border-radius:3px;padding:0 1px";
    static final String DEL = "background:rgba(220,38,38,.2);text-decoration:line-through;border-radius:3px;padding:0 1px";

    private DiffView() {
    }

    static String timeline(List<ItemHistory.Step> steps) {
        if (steps.isEmpty()) {
            return "<p style=\"opacity:.7\">Sin historial todavía.</p>";
        }
        var rows = steps.stream().map(s -> "<tr>"
                + td(s.seq() > 0 ? "#" + s.seq() : "—")
                + td(Ui.esc(Ui.when(s.at())))
                + td(Ui.esc(s.byName() == null ? "" : s.byName()))
                + td(Ui.chip(s.action().label(), color(s.action())) + (s.note() == null ? "" : " " + Ui.esc(s.note())))
                + td(s.version() == null ? "" : "Versión " + s.version())
                + td(s.revision() == null ? "" : "<code>" + Ui.esc(s.revision()) + "</code>")
                + "</tr>").collect(Collectors.joining());
        return "<table style=\"width:100%;border-collapse:collapse\"><thead><tr>"
                + th("Nº") + th("Cuándo") + th("Quién") + th("Qué") + th("Versión") + th("Se puede comparar como")
                + "</tr></thead><tbody>" + rows + "</tbody></table>";
    }

    static String color(io.mateu.ecdemo1.contenthub.domain.item.HistoryAction action) {
        return switch (action) {
            case PUBLISHED -> "#2e7d32";
            case RETIRED, REJECTED -> "#c74634";
            case SENT_TO_REVIEW -> "#b8860b";
            case RESTORED -> "#7c3aed";
            default -> "#3b82f6";
        };
    }

    static String changes(List<ContentDiff.Change> changes) {
        if (changes.isEmpty()) {
            return "<p>Sin diferencias.</p>";
        }
        var groups = new LinkedHashMap<String, StringBuilder>();
        for (var c : changes) {
            groups.computeIfAbsent(c.group(), g -> new StringBuilder()).append(change(c));
        }
        return groups.entrySet().stream()
                .map(e -> Ui.card("<h3 style=\"margin:0 0 .5rem\">" + Ui.esc(e.getKey()) + "</h3>" + e.getValue()))
                .collect(Collectors.joining());
    }

    static String change(ContentDiff.Change c) {
        var head = "<div style=\"margin:.5rem 0\"><b>" + Ui.esc(c.field()) + "</b> " + Ui.chip(kind(c.kind()), switch (c.kind()) {
            case ADDED -> "#2e7d32";
            case REMOVED -> "#c74634";
            case MOVED -> "#7c3aed";
            case CHANGED -> "#3b82f6";
        });
        String body;
        if (!c.text().isEmpty()) {
            body = "<div style=\"white-space:normal;line-height:1.6;margin-top:.25rem\">" + segments(c.text()) + "</div>";
        } else if (c.before() != null && c.after() != null) {
            body = " <del style=\"" + DEL + "\">" + Ui.esc(c.before()) + "</del> → <ins style=\"" + INS + "\">"
                    + Ui.esc(c.after()) + "</ins>";
        } else if (c.after() != null) {
            body = " <ins style=\"" + INS + "\">" + Ui.esc(c.after()) + "</ins>";
        } else if (c.before() != null) {
            body = " <del style=\"" + DEL + "\">" + Ui.esc(c.before()) + "</del>";
        } else {
            body = "";
        }
        return head + body + "</div>";
    }

    /** A word diff as markup: removed text struck in red, added text in green, line breaks kept. */
    static String segments(List<TextDiff.Segment> segments) {
        var out = new StringBuilder();
        for (var s : segments) {
            var text = Ui.esc(s.text()).replace("\n", "<br/>");
            switch (s.op()) {
                case EQUAL -> out.append(text);
                case ADDED -> out.append("<ins style=\"").append(INS).append("\">").append(text).append("</ins>");
                case REMOVED -> out.append("<del style=\"").append(DEL).append("\">").append(text).append("</del>");
            }
        }
        return out.toString();
    }

    static String kind(ContentDiff.Kind kind) {
        return switch (kind) {
            case ADDED -> "añadido";
            case REMOVED -> "quitado";
            case MOVED -> "movido";
            case CHANGED -> "cambiado";
        };
    }

    private static String td(String html) {
        return "<td style=\"padding:.35rem .5rem;border-bottom:1px solid rgba(128,128,128,.2)\">" + html + "</td>";
    }

    private static String th(String text) {
        return "<th style=\"text-align:left;padding:.35rem .5rem;border-bottom:1px solid rgba(128,128,128,.4)\">" + text + "</th>";
    }
}
