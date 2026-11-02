package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A page edited on the page itself: Mateu hosts {@code <ec-visual-editor>} — an Element, the renderers'
 * escape hatch for a web component, drawn the same by the Vaadin and the Redwood shell — and the component
 * does the rest against {@code /_intranet/api/editor} (VisualEditorController).
 *
 * <p>How the component's code arrives: {@code embed.js}, which both shells already load and which the
 * Element names as its {@code import} (so it is there even in a shell that does not), sees the tag and
 * fetches {@code visual-editor.js} with the user's token — the gateway lets only embed.js through without
 * one, and the editor's code is for editors.
 */
@Service
@Scope("prototype")
@Editors
public class VisualEditorPage implements Identifiable {

    public static final String API = "/_intranet/api/editor";

    @Hidden
    String id;
    @Hidden
    String title;

    @Label("")
    @Colspan(2)
    Callable<Component> editor = this::editor;

    final ItemRepository items;

    public VisualEditorPage(ItemRepository items) {
        this.items = items;
    }

    public VisualEditorPage load(String id) {
        var item = items.find(id).orElseThrow(() -> new IllegalArgumentException("No existe el contenido " + id));
        if (item.type() != ItemType.PAGE) {
            throw new IllegalArgumentException("El editor visual es para páginas");
        }
        this.id = id;
        this.title = item.draft().in(item.defaultLanguage(), item.defaultLanguage()).title();
        return this;
    }

    Component editor() {
        return new Element("ec-visual-editor", Map.of("page-id", id == null ? "" : id, "api", API,
                "import", "/_intranet/embed.js"), Map.of(), "", "display:block;width:100%", "", false);
    }

    /** Review and publication stay in the form. */
    @Toolbar
    @Action
    public Object volverAlFormulario(HttpRequest httpRequest) {
        return UICommand.navigateTo(formRoute(id));
    }

    public static String formRoute(String id) {
        return "/editorial/contenidos/" + id;
    }

    public static String route(String id) {
        return "/editorial/visual/" + id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Editor visual · " + (title == null ? id : title);
    }
}
