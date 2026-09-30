package io.mateu.ecdemo1.communication.ui.pages;

import io.mateu.ecdemo1.communication.store.Channel;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.application.Recipients;
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
    @Label("Incomplete check-ins (front desk)")
    @Help("A forced check-in still lacks a guest's document past the traveller's-registration deadline")
    boolean checkInIncomplete;
    @Label("Platform alerts")
    @Help("A Prometheus alert of the platform fired: an external API degraded (Salesforce, Opera)")
    boolean platformAlert;
    @Label("Critical platform alerts")
    @Help("Opera or Salesforce not answering, a service down, a pod crash-looping")
    boolean platformAlertCritical;
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
    @Label("Browser at the front desk (Web Push)")
    @Help("The front office's browsers of these users or roles")
    boolean byFrontDeskPush;
    @Label("E-mail")
    boolean byEmail;
    @Label("Google Chat")
    boolean byGoogleChat;
    @Label("Google Chat spaces")
    @Help("1, 2…; empty is every space")
    String chatSpaces;

    final Recipients recipients;

    public String save() {
        var channels = EnumSet.noneOf(Channel.class);
        if (byInbox) channels.add(Channel.INBOX);
        if (byWebPush) channels.add(Channel.WEB_PUSH);
        if (byFrontDeskPush) channels.add(Channel.FRONT_DESK_PUSH);
        if (byEmail) channels.add(Channel.EMAIL);
        if (byGoogleChat) channels.add(Channel.GOOGLE_CHAT);
        var types = new ArrayList<NotificationType>();
        if (causeOpened) types.add(NotificationType.CAUSE_OPENED);
        if (proposalReady) types.add(NotificationType.PROPOSAL_READY);
        if (retryingTooLong) types.add(NotificationType.RETRYING_TOO_LONG);
        if (pmsRejected) types.add(NotificationType.PMS_REJECTED);
        if (integrationNeedsAttention) types.add(NotificationType.INTEGRATION_NEEDS_ATTENTION);
        if (checkInIncomplete) types.add(NotificationType.CHECK_IN_INCOMPLETE);
        if (platformAlert) types.add(NotificationType.PLATFORM_ALERT);
        if (platformAlertCritical) types.add(NotificationType.PLATFORM_ALERT_CRITICAL);
        return recipients.save(new Recipients.RecipientChange(id, name, active, blankToNull(users), blankToNull(roles),
                blankToNull(email), types, tasks, hotelCode == null || hotelCode.isBlank() ? null : hotelCode.trim(),
                channels, byGoogleChat ? blankToNull(chatSpaces) : null));
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
        checkInIncomplete = types.contains(NotificationType.CHECK_IN_INCOMPLETE.name());
        platformAlert = types.contains(NotificationType.PLATFORM_ALERT.name());
        platformAlertCritical = types.contains(NotificationType.PLATFORM_ALERT_CRITICAL.name());
        tasks = r.tasks;
        hotelCode = r.hotelCode;
        var channels = r.channelSet();
        byInbox = channels.contains(Channel.INBOX);
        byWebPush = channels.contains(Channel.WEB_PUSH);
        byFrontDeskPush = channels.contains(Channel.FRONT_DESK_PUSH);
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
