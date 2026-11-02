package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.history.ItemHistory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.security.Writers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Button;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.OptionsSupplier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * An item's history (/editorial/historial/{id}): every step, who and when; two of its states compared
 * (a version, a saved draft or the current draft), field by field and language by language; and
 * «Restaurar esta versión», which copies one into the draft — never publishes. Viewing is for any
 * editorial permission; restoring, for content:edit.
 */
@Service
@Scope("prototype")
@Editors
public class HistoryPage implements Identifiable, OptionsSupplier, io.mateu.core.domain.RequestBound {

    @Hidden
    String id;
    @Hidden
    String title;

    @Section("Comparar")
    @Label("Desde")
    @Stereotype(FieldStereotype.select)
    String desde;
    @Label("Hasta")
    @Stereotype(FieldStereotype.select)
    String hasta = ItemHistory.DRAFT;

    @Section("Restaurar")
    @Label("Versión a restaurar en el borrador")
    @Stereotype(FieldStereotype.select)
    String aRestaurar;

    // one Element per screen (Html): the history and the comparison are drawn together
    @Section("Historial y diferencias")
    @Label("")
    @Colspan(2)
    Callable<Component> historial = this::draw;

    final ItemHistory history;
    final ItemRepository items;
    final Readers readers;

    // the request the page is drawn for: wiring, not state (transient keeps it off the wire)
    private transient HttpRequest request;

    public HistoryPage(ItemHistory history, ItemRepository items, Readers readers) {
        this.history = history;
        this.items = items;
        this.readers = readers;
    }

    public HistoryPage load(String id, HttpRequest httpRequest) {
        var item = items.find(id).orElseThrow(() -> new IllegalArgumentException("No existe el contenido " + id));
        this.id = id;
        this.title = item.draft().in(item.defaultLanguage(), item.defaultLanguage()).title();
        this.request = httpRequest;
        // by default: what readers see now (the last version) against the draft
        this.desde = item.latestVersion().map(v -> "v" + v.number()).orElse(null);
        return this;
    }

    @Override
    public void bindRequest(HttpRequest httpRequest) {
        this.request = httpRequest;
    }

    public static String route(String id) {
        return "/editorial/historial/" + id;
    }

    Component draw() {
        if (id == null) {
            return Html.block("");
        }
        var reader = readers.of(request);
        var markup = new StringBuilder("<h3 style=\"margin-top:0\">Historial</h3>")
                .append(DiffView.timeline(history.timeline(reader, id)));
        if (desde != null && !desde.isBlank() && hasta != null && !hasta.isBlank()) {
            var labels = history.revisions(reader, id);
            markup.append("<h3>Diferencias: ").append(Ui.esc(label(labels, desde))).append(" → ")
                    .append(Ui.esc(label(labels, hasta))).append("</h3>");
            try {
                markup.append(DiffView.changes(history.compare(reader, id, desde, hasta)));
            } catch (IllegalArgumentException e) {
                markup.append("<p>").append(Ui.esc(e.getMessage())).append("</p>");
            }
        } else {
            markup.append("<p style=\"opacity:.7\">Elige «Desde» y «Hasta» y pulsa «Comparar».</p>");
        }
        return Html.block(markup.toString());
    }

    static String label(List<ItemHistory.Revision> revisions, String key) {
        return revisions.stream().filter(r -> r.key().equals(key)).map(ItemHistory.Revision::label).findFirst().orElse(key);
    }

    @Button
    @Action
    public Object comparar(HttpRequest httpRequest) {
        request = httpRequest;
        if (desde == null || desde.isBlank() || hasta == null || hasta.isBlank()) {
            return Message.warning("Elige qué comparar: «Desde» y «Hasta»");
        }
        return this;
    }

    @Button
    @Writers
    @Action(confirmationRequired = true, confirmationTitle = "¿Restaurar esta versión?",
            confirmationMessage = "Se copia en el borrador: no se publica. Luego sigue el flujo de siempre (revisión, publicación).")
    public Object restaurarEstaVersion(HttpRequest httpRequest) {
        request = httpRequest;
        if (aRestaurar == null || aRestaurar.isBlank()) {
            return Message.warning("Elige en «Restaurar» la versión que quieres recuperar");
        }
        var restored = history.restore(readers.of(httpRequest), id, aRestaurar);
        desde = aRestaurar;
        hasta = ItemHistory.DRAFT;
        aRestaurar = null;
        var out = new ArrayList<Object>();
        out.add(this);
        out.add(Message.success(restored.message()));
        restored.warnings().forEach(w -> out.add(Message.warning(w)));
        return out;
    }

    @Toolbar
    @Action
    public Object volverAlContenido(HttpRequest httpRequest) {
        return UICommand.navigateTo(VisualEditorPage.formRoute(id));
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        if (id == null) {
            return List.of();
        }
        var revisions = history.revisions(readers.of(httpRequest), id);
        return revisions.stream()
                .filter(r -> !"aRestaurar".equals(fieldName) || !ItemHistory.DRAFT.equals(r.key()))
                .map(r -> new Option(r.key(), r.label())).toList();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Historial · " + (title == null ? id : title);
    }
}
