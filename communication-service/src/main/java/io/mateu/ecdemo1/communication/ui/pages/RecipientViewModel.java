package io.mateu.ecdemo1.communication.ui.pages;

import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.interfaces.Identifiable;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** A recipient. Leave the type or the hotel empty for "any". */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class RecipientViewModel implements Identifiable {

    @NotEmpty
    String name;
    @NotEmpty
    String email;
    NotificationType notificationType;
    String hotelCode;
    boolean active = true;
    @ReadOnly
    @HiddenInCreate
    String id;

    final RecipientRepository recipients;

    public String save() {
        var r = id == null ? new Recipient() : recipients.findById(id).orElseGet(Recipient::new);
        r.id = id == null ? UUID.randomUUID().toString() : id;
        r.name = name;
        r.email = email;
        r.notificationType = notificationType;
        r.hotelCode = hotelCode == null || hotelCode.isBlank() ? null : hotelCode;
        r.active = active;
        return recipients.save(r).id;
    }

    public RecipientViewModel load(Recipient r) {
        id = r.id;
        name = r.name;
        email = r.email;
        notificationType = r.notificationType;
        hotelCode = r.hotelCode;
        active = r.active;
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "New recipient" : name;
    }
}
