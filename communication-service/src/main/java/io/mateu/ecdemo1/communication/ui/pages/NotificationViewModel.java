package io.mateu.ecdemo1.communication.ui.pages;

import io.mateu.ecdemo1.communication.send.Deliveries;
import io.mateu.ecdemo1.communication.store.Notification;
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

@Service
@Scope("prototype")
@RequiredArgsConstructor
public class NotificationViewModel implements Identifiable {

    @ReadOnly
    Status status = new Status(StatusType.NONE, "");

    @Section("Notification")
    @ReadOnly
    String type;
    @ReadOnly
    String hotel;
    @ReadOnly
    String title;
    @ReadOnly
    @Stereotype(FieldStereotype.textarea)
    String body;
    @ReadOnly
    String link;

    @Section("Delivery")
    @ReadOnly
    String recipients;
    @ReadOnly
    String requestedAt;
    @ReadOnly
    String sentAt;
    @ReadOnly
    Integer attempts;
    @ReadOnly
    String lastError;
    @ReadOnly
    String id;

    final Deliveries deliveries;

    @Toolbar
    @Action
    public Object resend(HttpRequest httpRequest) {
        load(deliveries.resend(id));
        return List.of(new Message("Sent again, to whoever should receive it now"), new State(this));
    }

    public NotificationViewModel load(Notification n) {
        status = NotificationsPage.status(n);
        type = String.valueOf(n.type);
        hotel = n.hotelCode;
        title = n.title;
        body = n.body;
        link = n.link;
        recipients = n.recipients;
        requestedAt = NotificationsPage.when(n.requestedAt);
        sentAt = NotificationsPage.when(n.sentAt);
        attempts = n.attempts;
        lastError = n.lastError;
        id = n.id;
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return title;
    }
}
