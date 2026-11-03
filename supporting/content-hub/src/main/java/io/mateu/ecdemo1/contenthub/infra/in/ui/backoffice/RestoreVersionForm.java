package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.history.ItemHistory;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.security.Writers;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.Dialog;
import io.mateu.uidl.data.EmbeddedView;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.interfaces.ButtonsSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.TitleSupplier;
import io.mateu.uidl.fluent.UserTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** «Restaurar una versión», in a dialog: which one. It only ever goes to the draft, and the workflow goes on from there. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Writers
public class RestoreVersionForm implements TitleSupplier, ButtonsSupplier, OptionsSupplier {

    @Hidden
    String itemId;

    @Label("Versión")
    @Stereotype(FieldStereotype.select)
    @Help("Se copia en el borrador: no se publica. Luego sigue el flujo de siempre (revisión, publicación).")
    String version;

    final ItemHistory history;
    final Readers readers;

    Dialog dialogFor(String itemId) {
        this.itemId = itemId;
        return Dialog.builder().width("32rem").content(new EmbeddedView(this)).build();
    }

    @Override
    public String title() {
        return "Restaurar una versión";
    }

    @Override
    public Collection<UserTrigger> buttons() {
        return List.of(
                Button.builder().label("Restaurar en el borrador").actionId("restaurar").buttonStyle(ButtonStyle.primary).build(),
                Button.builder().label("Cancelar").actionId("cancelar").buttonStyle(ButtonStyle.tertiary).build());
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return RestoreVersionForm.class.equals(formType) && "version".equals(fieldName);
    }

    /** Whatever can be gone back to: not the draft itself. */
    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return history.revisions(readers.of(httpRequest), itemId).stream()
                .filter(r -> !ItemHistory.DRAFT.equals(r.key()))
                .map(r -> new Option(r.key(), r.label())).toList();
    }

    @Action
    public Object restaurar(HttpRequest httpRequest) {
        if (version == null || version.isBlank()) {
            return Message.warning("Elige la versión a restaurar");
        }
        var restored = history.restore(readers.of(httpRequest), itemId, version);
        var out = new ArrayList<Object>();
        out.add(Message.success(restored.message()));
        restored.warnings().forEach(w -> out.add(Message.warning(w)));
        out.add(UICommand.closeModal());
        out.add(UICommand.navigateTo(HistoryPage.route(itemId)));
        return out;
    }

    @Action
    public UICommand cancelar() {
        return UICommand.closeModal();
    }
}
