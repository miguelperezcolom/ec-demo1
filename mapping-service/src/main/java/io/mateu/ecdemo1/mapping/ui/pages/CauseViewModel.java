package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.store.CauseRecord;
import io.mateu.ecdemo1.mapping.store.CauseStatus;
import io.mateu.ecdemo1.mapping.store.WaiterRepository;
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
public class CauseViewModel implements Identifiable {

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
    final WaiterRepository waiters;

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Resolve this cause?",
            confirmationMessage = "Every process waiting only on it resumes and reads its data again.")
    public Object resolve(HttpRequest httpRequest) {
        causes.resolve(key, EntryViewModel.user(httpRequest));
        return List.of(new Message("Resolved: the processes behind it resume"), new State(this));
    }

    public CauseViewModel load(CauseRecord cause) {
        status = cause.status == CauseStatus.OPEN ? new Status(StatusType.WARNING, "Open")
                : new Status(StatusType.SUCCESS, "Resolved");
        key = cause.causeKey;
        type = cause.type.name();
        description = cause.description;
        hotel = cause.hotelCode;
        openedAt = String.valueOf(cause.openedAt);
        resolved = cause.resolvedAt == null ? "" : cause.resolvedAt + " by " + cause.resolvedBy;
        waiting = waiters.waitingOn(cause.causeKey).stream()
                .map(w -> new WaitingRow(w.processKey, w.definitionId, w.subject, String.valueOf(w.createdAt)))
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
