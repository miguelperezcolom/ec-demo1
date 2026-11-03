package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.history.ItemHistory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.security.Writers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldDataType;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.DetailContentSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * An item's history (/editorial/historial/{id}): every step, who and when; two of its states compared
 * (a version, a saved draft or the current draft), field by field and language by language; and
 * «Restaurar una versión», a dialog (RestoreVersionForm) that copies one into the draft — never publishes. Viewing is for any
 * editorial permission; restoring, for content:edit.
 *
 * <p>The listing opens this as its record's page, which Mateu draws read-only from the fields: the
 * selects are fluent {@link FormField}s in {@link #detailContent} instead (as ApprovalPage does), bound
 * to the hidden fields the actions read.
 */
@Service
@Scope("prototype")
@Editors
public class HistoryPage implements Identifiable, DetailContentSupplier {

    @Hidden
    String id;
    @Hidden
    String title;
    @Hidden
    String desde;
    @Hidden
    String hasta = ItemHistory.DRAFT;

    final ItemHistory history;
    final ItemRepository items;
    final Readers readers;
    final org.springframework.beans.factory.ObjectProvider<RestoreVersionForm> restoreForm;

    public HistoryPage(ItemHistory history, ItemRepository items, Readers readers,
                       org.springframework.beans.factory.ObjectProvider<RestoreVersionForm> restoreForm) {
        this.restoreForm = restoreForm;
        this.history = history;
        this.items = items;
        this.readers = readers;
    }

    public HistoryPage load(String id, HttpRequest httpRequest) {
        var item = items.find(id).orElseThrow(() -> new IllegalArgumentException("No existe el contenido " + id));
        this.id = id;
        this.title = item.draft().in(item.defaultLanguage(), item.defaultLanguage()).title();
        // by default: what readers see now (the last version) against the draft
        this.desde = item.latestVersion().map(v -> "v" + v.number()).orElse(null);
        this.hasta = ItemHistory.DRAFT;
        return this;
    }

    public static String route(String id) {
        return "/editorial/historial/" + id;
    }

    @Override
    public Component detailContent(HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        var revisions = history.revisions(reader, id);
        var options = revisions.stream().map(r -> new Option(r.key(), r.label())).toList();
        var content = new ArrayList<Component>();
        // In a form layout: it's where Redwood draws a fluent field as what it is, a select
        content.add(io.mateu.uidl.data.FormLayout.builder().maxColumns(2).content(List.of(
                select("desde", "Desde", options, desde),
                select("hasta", "Hasta", options, hasta))).build());
        content.add(Html.block(markup(reader, revisions)));
        return VerticalLayout.builder().fullWidth(true).spacing(true).content(content).build();
    }

    static Component select(String id, String label, List<Option> options, String value) {
        return FormField.builder().id(id).label(label).dataType(FieldDataType.string).stereotype(FieldStereotype.select)
                .options(options).initialValue(value).style("min-width: 18rem;").build();
    }

    /** One Element per screen (Html): the comparison and the history drawn together. */
    String markup(io.mateu.ecdemo1.contenthub.application.security.Reader reader, List<ItemHistory.Revision> revisions) {
        var markup = new StringBuilder();
        if (desde != null && !desde.isBlank() && hasta != null && !hasta.isBlank()) {
            markup.append("<h3 style=\"margin-top:0\">Diferencias: ").append(Ui.esc(label(revisions, desde))).append(" → ")
                    .append(Ui.esc(label(revisions, hasta))).append("</h3>");
            try {
                markup.append(DiffView.changes(history.compare(reader, id, desde, hasta)));
            } catch (IllegalArgumentException e) {
                markup.append("<p>").append(Ui.esc(e.getMessage())).append("</p>");
            }
        } else {
            markup.append("<p style=\"opacity:.7\">Elige «Desde» y «Hasta» y pulsa «Comparar».</p>");
        }
        markup.append("<h3>Historial</h3>").append(DiffView.timeline(history.timeline(reader, id)));
        return markup.toString();
    }

    static String label(List<ItemHistory.Revision> revisions, String key) {
        return revisions.stream().filter(r -> r.key().equals(key)).map(ItemHistory.Revision::label).findFirst().orElse(key);
    }

    @Toolbar
    @Action
    public Object comparar(HttpRequest httpRequest) {
        if (desde == null || desde.isBlank() || hasta == null || hasta.isBlank()) {
            return List.of(this, Message.warning("Elige qué comparar: «Desde» y «Hasta»"));
        }
        return this;
    }

    /** Which version, in a dialog: it only matters when restoring. */
    @Toolbar
    @Writers
    @Action
    @Label("Restaurar una versión")
    public Object restaurarUnaVersion(HttpRequest httpRequest) {
        return restoreForm.getObject().dialogFor(id);
    }

    @Toolbar
    @Action
    public Object volverAlContenido(HttpRequest httpRequest) {
        return UICommand.navigateTo(VisualEditorPage.formRoute(id));
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
