package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.store.CauseRecord;
import io.mateu.ecdemo1.mapping.store.CauseStatus;
import io.mateu.ecdemo1.mapping.queries.CauseQueries;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import io.mateu.uidl.data.ColumnAction;
import io.mateu.uidl.data.ColumnActionGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * One cause and the processes waiting on it. A missing equivalence is resolved by approving it in
 * the dictionary, a missing partner by projecting the partner; the Resolve action here is for what
 * nothing else resolves — a write the PMS refused, once someone fixed why.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class CauseViewModel implements Identifiable, VisibilitySupplier {

    @ReadOnly
    Status status = new Status(StatusType.NONE, "");

    @Section("Cause")
    @ReadOnly
    String key;
    @ReadOnly
    String type;
    @ReadOnly
    @Stereotype(FieldStereotype.textarea)
    String description;
    @ReadOnly
    String hotel;
    @ReadOnly
    String openedAt;
    @ReadOnly
    String resolved;

    @Section("Waiting")
    @ReadOnly
    @Stereotype(FieldStereotype.grid)
    List<WaitingRow> waiting;

    final Causes causes;
    final CauseQueries queries;
    final DiscardForm discardForm;

    /**
     * Gives up on a waiting process — the row's, or one picked in the dialog, or all of them — after
     * saying what that means and asking why (F012). Offered while any process waits on the cause or
     * was released by it and has not answered.
     */
    @Toolbar
    @Action
    @io.mateu.uidl.annotations.Label("Descartar…")
    public Object discard(HttpRequest httpRequest) {
        return discardForm.dialogFor(key, null, route(httpRequest));
    }

    @Override
    public boolean isHidden(String memberName, HttpRequest httpRequest) {
        if ("discard".equals(memberName)) {
            return waiting == null || waiting.isEmpty();
        }
        if ("resolve".equals(memberName)) {
            return "Resolved".equals(status == null ? null : status.message());
        }
        return false;
    }

    /** The row's process in both renderers' contracts: the whole row as {@code _clickedRow}, or its fields directly. */
    static String clickedProcess(HttpRequest httpRequest) {
        var parameters = httpRequest.runActionRq().parameters();
        if (parameters == null) {
            return null;
        }
        if (parameters.get("_clickedRow") instanceof java.util.Map<?, ?> row && row.get("process") != null) {
            return String.valueOf(row.get("process"));
        }
        var direct = parameters.get("process");
        return direct == null ? null : String.valueOf(direct);
    }

    /** Where the dialog takes the operator back to: this cause, re-read. */
    static String route(HttpRequest httpRequest) {
        var route = httpRequest.runActionRq().route();
        return route == null || route.isBlank() ? "/mapping/causes" : route;
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Resolve this cause?",
            confirmationMessage = "Every process waiting only on it resumes and reads its data again.")
    public Object resolve(HttpRequest httpRequest) {
        causes.resolve(key, EntryViewModel.user(httpRequest));
        return List.of(new Message("Resolved: the processes behind it resume"), new State(this));
    }

    public CauseViewModel load(CauseRecord cause) {
        return load(cause, true);
    }

    /** @param rowActions whether each waiting process's row carries its «Descartar» (see {@link CausesPage#redwood}) */
    public CauseViewModel load(CauseRecord cause, boolean rowActions) {
        status = cause.status == CauseStatus.OPEN ? new Status(StatusType.WARNING, "Open")
                : new Status(StatusType.SUCCESS, "Resolved");
        key = cause.causeKey;
        type = cause.type.name();
        description = cause.description;
        hotel = cause.hotelCode;
        openedAt = String.valueOf(cause.openedAt);
        resolved = cause.resolvedAt == null ? "" : cause.resolvedAt + " by " + cause.resolvedBy;
        waiting = queries.processesPendingOn(cause.causeKey).stream()
                .map(w -> new WaitingRow(w.processKey, w.definitionId, w.subject, String.valueOf(w.createdAt),
                        w.status.name(), w.engineProcessId == null ? "unknown" : w.engineProcessId,
                        rowActions ? new ColumnActionGroup(new ColumnAction[] {new ColumnAction("discardWaiter", "Descartar")})
                                : null))
                .toList();
        return this;
    }

    @Override
    public String id() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}
