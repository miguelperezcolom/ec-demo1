package io.mateu.ecdemo1.communication.ui.pages;

import io.mateu.ecdemo1.communication.store.Channel;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.interfaces.Identifiable;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;

/**
 * A recipient: who is told, about what, and where. Every active recipient that wants a notification
 * gets it on each of its channels; nothing else decides who hears of what.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class RecipientViewModel implements Identifiable {

    @Section("Recipient")
    @NotEmpty
    String name;
    boolean active = true;
    @ReadOnly
    @HiddenInCreate
    String id;

    @Section("Who")
    @Label("Users")
    @Help("Keycloak usernames, comma-separated")
    String users;
    @Label("Roles")
    @Help("Realm roles, comma-separated: everyone who has one of them")
    String roles;
    @Label("E-mail")
    @Help("Where the E-mail channel goes")
    String email;

    @Section("What")
    @Help("No type ticked is every type")
    @Label("Causes opened")
    boolean causeOpened;
    @Label("Mapping proposals ready")
    boolean proposalReady;
    @Label("Retrying too long")
    boolean retryingTooLong;
    @Label("Rejected by the PMS")
    boolean pmsRejected;
    @Label("Integrations that need attention")
    boolean integrationNeedsAttention;
    @Label("Tasks of the forms engine")
    boolean tasks;
    @Label("Hotel")
    @Help("Empty is every hotel")
    String hotelCode;

    @Section("Where")
    @Label("Inbox")
    boolean byInbox = true;
    @Label("Browser (Web Push)")
    boolean byWebPush;
    @Label("E-mail")
    boolean byEmail;
    @Label("Google Chat")
    boolean byGoogleChat;
    @Label("Google Chat spaces")
    @Help("1, 2…; empty is every space")
    String chatSpaces;

    final RecipientRepository recipients;

    public String save() {
        var channels = EnumSet.noneOf(Channel.class);
        if (byInbox) channels.add(Channel.INBOX);
        if (byWebPush) channels.add(Channel.WEB_PUSH);
        if (byEmail) channels.add(Channel.EMAIL);
        if (byGoogleChat) channels.add(Channel.GOOGLE_CHAT);
        var problems = problems(channels, blankToNull(users), blankToNull(roles), blankToNull(email));
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
        var r = id == null ? new Recipient() : recipients.findById(id).orElseGet(Recipient::new);
        r.id = id == null ? UUID.randomUUID().toString() : id;
        r.name = name;
        r.active = active;
        r.users = blankToNull(users);
        r.roles = blankToNull(roles);
        r.email = blankToNull(email);
        var types = new ArrayList<NotificationType>();
        if (causeOpened) types.add(NotificationType.CAUSE_OPENED);
        if (proposalReady) types.add(NotificationType.PROPOSAL_READY);
        if (retryingTooLong) types.add(NotificationType.RETRYING_TOO_LONG);
        if (pmsRejected) types.add(NotificationType.PMS_REJECTED);
        if (integrationNeedsAttention) types.add(NotificationType.INTEGRATION_NEEDS_ATTENTION);
        r.types = Recipient.join(types);
        r.tasks = tasks;
        r.hotelCode = hotelCode == null || hotelCode.isBlank() ? null : hotelCode.trim();
        r.channels = Recipient.join(channels);
        r.chatSpaces = byGoogleChat ? blankToNull(chatSpaces) : null;
        return recipients.save(r).id;
    }

    /** What a recipient needs to reach anyone on its channels. */
    public static java.util.List<String> problems(EnumSet<Channel> channels, String users, String roles, String email) {
        var problems = new ArrayList<String>();
        if (channels.isEmpty()) {
            problems.add("Tick at least one channel; to silence a recipient, deactivate it.");
        }
        if ((channels.contains(Channel.INBOX) || channels.contains(Channel.WEB_PUSH)) && users == null && roles == null) {
            problems.add("Inbox and Web Push need users or roles.");
        }
        if (channels.contains(Channel.EMAIL) && email == null) {
            problems.add("E-mail needs an address.");
        }
        return problems;
    }

    public RecipientViewModel load(Recipient r) {
        id = r.id;
        name = r.name;
        active = r.active;
        users = r.users;
        roles = r.roles;
        email = r.email;
        var types = r.typeList();
        causeOpened = types.contains(NotificationType.CAUSE_OPENED.name());
        proposalReady = types.contains(NotificationType.PROPOSAL_READY.name());
        retryingTooLong = types.contains(NotificationType.RETRYING_TOO_LONG.name());
        pmsRejected = types.contains(NotificationType.PMS_REJECTED.name());
        integrationNeedsAttention = types.contains(NotificationType.INTEGRATION_NEEDS_ATTENTION.name());
        tasks = r.tasks;
        hotelCode = r.hotelCode;
        var channels = r.channelSet();
        byInbox = channels.contains(Channel.INBOX);
        byWebPush = channels.contains(Channel.WEB_PUSH);
        byEmail = channels.contains(Channel.EMAIL);
        byGoogleChat = channels.contains(Channel.GOOGLE_CHAT);
        chatSpaces = r.chatSpaces;
        return this;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.replace(" ", "");
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
