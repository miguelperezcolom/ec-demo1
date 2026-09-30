package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.queries.CauseQueries;
import io.mateu.ecdemo1.mapping.store.CauseStatus;
import io.mateu.ecdemo1.mapping.store.Waiter;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonColor;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.Dialog;
import io.mateu.uidl.data.EmbeddedView;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.UserTrigger;
import io.mateu.uidl.interfaces.ButtonsSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.TitleSupplier;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Gives up on a process waiting on a cause — or on all of them — for a reason (F012). Opened in a
 * dialog from the cause: from a waiting process's row, or from the toolbar. It says what discarding
 * means before anyone does it, and when nobody is left waiting on the cause, offers to resolve it.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class DiscardForm implements TitleSupplier, ButtonsSupplier, VisibilitySupplier, OptionsSupplier {

    /** The value of {@link #process} that means every process on the cause. */
    static final String ALL = "*";

    static final String CONSEQUENCE = """
            A discarded process is given up: it is not resumed when the cause is resolved, it is not \
            sent anything again, and the engine is asked to cancel it. What it was doing does not reach \
            the PMS — if it is still needed, it has to be started again. It cannot be undone.""";

    @ReadOnly
    @Label("What it means")
    @Stereotype(FieldStereotype.textarea)
    String consequence = CONSEQUENCE;

    @Label("Process")
    @Stereotype(FieldStereotype.select)
    String process;

    @Label("Reason")
    @Stereotype(FieldStereotype.textarea)
    @Help("Kept with your name on the process, and in the audit")
    String reason;

    @Label("Resolve the cause too, if no process is left waiting on it")
    boolean resolveCause;

    @Hidden
    String causeKey;

    /** Where to go once done: the cause, again. */
    @Hidden
    String returnTo;

    final Causes causes;
    final CauseQueries queries;

    /** The dialog for this cause, with {@code processKey} picked (null: the first, or all when there is one). */
    Dialog dialogFor(String causeKey, String processKey, String returnTo) {
        this.causeKey = causeKey;
        this.returnTo = returnTo;
        var pending = pending();
        this.process = processKey != null ? processKey : pending.size() == 1 ? pending.get(0).getProcessKey() : null;
        return Dialog.builder()
                .width("40rem")
                .content(new EmbeddedView(this))
                .build();
    }

    @Override
    public String title() {
        return ALL.equals(process) ? "Discard every process waiting on " + causeKey
                : process == null ? "Discard a process waiting on " + causeKey : "Discard " + process;
    }

    @Override
    public Collection<UserTrigger> buttons() {
        return List.of(
                Button.builder().label("Descartar").actionId("discard")
                        .buttonStyle(ButtonStyle.primary).color(ButtonColor.error).build(),
                Button.builder().label("Keep waiting").actionId("keep").buttonStyle(ButtonStyle.tertiary).build());
    }

    /** Resolving is offered only while the cause is open. */
    @Override
    public boolean isHidden(String memberName, HttpRequest httpRequest) {
        return "resolveCause".equals(memberName)
                && queries.cause(causeKey).map(c -> c.status != CauseStatus.OPEN).orElse(true);
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return DiscardForm.class.equals(formType) && "process".equals(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        if (!"process".equals(fieldName)) {
            return List.of();
        }
        var pending = pending();
        var options = new ArrayList<Option>();
        pending.forEach(w -> options.add(new Option(w.getProcessKey(),
                w.getProcessKey() + (w.getStatus() == io.mateu.ecdemo1.mapping.store.WaiterStatus.RELEASED ? " (released, not answering)" : ""))));
        if (pending.size() > 1) {
            options.add(new Option(ALL, "All " + pending.size() + " of them"));
        }
        return options;
    }

    @Action
    public Object discard(HttpRequest httpRequest) {
        if (process == null || process.isBlank()) {
            return Message.error("Pick the process to discard");
        }
        if (reason == null || reason.isBlank()) {
            return Message.error("Say why it is discarded");
        }
        var by = EntryViewModel.user(httpRequest);
        var done = ALL.equals(process) ? causes.discardAllWaitingOn(causeKey, reason, by)
                : List.of(causes.discard(process, reason, by));
        return List.of(outcome(done, by), UICommand.closeModal(), UICommand.navigateTo(returnTo));
    }

    @Action
    public UICommand keep() {
        return UICommand.closeModal();
    }

    Message outcome(List<Causes.Discarded> done, String by) {
        var parts = new ArrayList<String>();
        parts.add("Discarded: " + String.join(", ", done.stream().map(Causes.Discarded::processKey).toList()));
        var notCancelled = done.stream().filter(d -> !d.engineCancelRequested()).map(Causes.Discarded::processKey).toList();
        if (notCancelled.size() < done.size()) {
            parts.add("the engine was asked to cancel " + (notCancelled.isEmpty() ? "it" : "the others"));
        }
        if (!notCancelled.isEmpty()) {
            parts.add("cancel " + String.join(", ", notCancelled) + " by hand in Admin → Processes ("
                    + done.get(0).adminProcessesUrl() + "): the engine's id of it is not known here");
        }
        var unwaited = new LinkedHashSet<String>();
        done.forEach(d -> unwaited.addAll(d.causesLeftUnwaited()));
        if (resolveCause && unwaited.remove(causeKey)) {
            causes.resolve(causeKey, by);
            parts.add(causeKey + " resolved: no process waits on it any more");
        }
        if (!unwaited.isEmpty()) {
            parts.add("no process waits on " + String.join(", ", unwaited) + " any more: resolve "
                    + (unwaited.size() == 1 ? "it" : "them") + " if " + (unwaited.size() == 1 ? "it does" : "they do")
                    + " not matter now");
        }
        var text = String.join("; ", parts);
        return notCancelled.isEmpty() ? Message.success(text) : Message.warning(text);
    }

    List<Waiter> pending() {
        return causeKey == null ? List.of() : queries.processesPendingOn(causeKey);
    }
}
