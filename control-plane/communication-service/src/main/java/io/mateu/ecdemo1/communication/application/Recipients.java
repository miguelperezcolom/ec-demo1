package io.mateu.ecdemo1.communication.application;

import io.mateu.ecdemo1.communication.store.Channel;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Changing who is told what and where: adding a recipient, changing one, removing some. What every
 * screen and tool that changes a recipient goes through, so that a recipient that could reach
 * nobody is refused the same wherever it comes from.
 */
@Service
@RequiredArgsConstructor
public class Recipients {

    /**
     * A recipient as it should be. {@code id} null is a new one. The lists are as they are kept:
     * comma-separated, null when empty.
     */
    public record RecipientChange(String id, String name, boolean active, String users, String roles, String email,
                                  Collection<NotificationType> types, boolean tasks, String hotelCode,
                                  Set<Channel> channels, String chatSpaces) {
    }

    final RecipientRepository recipients;

    /** Saves it, and answers its id. A recipient that could reach nobody is refused, saying why. */
    @Transactional
    public String save(RecipientChange change) {
        var channels = change.channels() == null || change.channels().isEmpty()
                ? EnumSet.noneOf(Channel.class) : EnumSet.copyOf(change.channels());
        var problems = problems(channels, change.users(), change.roles(), change.email());
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
        var r = change.id() == null ? new Recipient() : recipients.findById(change.id()).orElseGet(Recipient::new);
        r.id = change.id() == null ? UUID.randomUUID().toString() : change.id();
        r.name = change.name();
        r.active = change.active();
        r.users = change.users();
        r.roles = change.roles();
        r.email = change.email();
        r.types = change.types() == null ? null : Recipient.join(change.types());
        r.tasks = change.tasks();
        r.hotelCode = change.hotelCode();
        r.channels = Recipient.join(channels);
        r.chatSpaces = change.chatSpaces();
        return recipients.save(r).id;
    }

    @Transactional
    public void delete(Collection<String> ids) {
        recipients.deleteAllById(ids);
    }

    /** What a recipient needs to reach anyone on its channels. */
    public static List<String> problems(Set<Channel> channels, String users, String roles, String email) {
        var problems = new ArrayList<String>();
        if (channels.isEmpty()) {
            problems.add("Tick at least one channel; to silence a recipient, deactivate it.");
        }
        if ((channels.contains(Channel.INBOX) || channels.contains(Channel.WEB_PUSH) || channels.contains(Channel.FRONT_DESK_PUSH))
                && users == null && roles == null) {
            problems.add("Inbox and Web Push need users or roles.");
        }
        if (channels.contains(Channel.EMAIL) && email == null) {
            problems.add("E-mail needs an address.");
        }
        return problems;
    }
}
