package io.mateu.ecdemo1.communication.routing;

import io.mateu.ecdemo1.communication.store.Channel;
import io.mateu.ecdemo1.communication.store.Recipient;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where one item goes, worked out from the recipients that want it: whose inbox shows it, whose
 * browsers are told, which addresses are e-mailed and which chat spaces get it. Each is a set: an item
 * goes once per person, browser, address and space, however many recipients ask for it.
 *
 * @param inboxUsers  the people whose inbox shows it, by username
 * @param inboxRoles  the roles whose inbox shows it
 * @param pushUsers   the people whose browsers are told
 * @param pushRoles   the roles whose browsers are told
 * @param emails      the addresses it is e-mailed to
 * @param spaces      the Google Chat spaces it is posted to, by number
 * @param anyone      whether any recipient wants it at all; nobody, and the gap shows
 */
public record Plan(Set<String> inboxUsers, Set<String> inboxRoles, Set<String> pushUsers, Set<String> pushRoles,
                   Set<String> emails, Set<Integer> spaces, boolean anyone) {

    /** The plan for an item of this type — a notification type's name, or TASK — and hotel. */
    public static Plan of(Collection<Recipient> recipients, String type, String hotel, int configuredSpaces) {
        var inboxUsers = new LinkedHashSet<String>();
        var inboxRoles = new LinkedHashSet<String>();
        var pushUsers = new LinkedHashSet<String>();
        var pushRoles = new LinkedHashSet<String>();
        var emails = new LinkedHashSet<String>();
        var spaces = new LinkedHashSet<Integer>();
        var anyone = false;
        for (var r : recipients) {
            if (!r.wants(type, hotel)) {
                continue;
            }
            anyone = true;
            if (r.by(Channel.INBOX)) {
                inboxUsers.addAll(r.userList());
                inboxRoles.addAll(r.roleList());
            }
            if (r.by(Channel.WEB_PUSH)) {
                pushUsers.addAll(r.userList());
                pushRoles.addAll(r.roleList());
            }
            if (r.by(Channel.EMAIL) && r.email != null && !r.email.isBlank()) {
                emails.add(r.email.trim());
            }
            if (r.by(Channel.GOOGLE_CHAT)) {
                spaces.addAll(r.spaces(configuredSpaces));
            }
        }
        return new Plan(inboxUsers, inboxRoles, pushUsers, pushRoles, emails, spaces, anyone);
    }

    /** Urgent is what somebody asked to be e-mailed about: it cannot wait for someone to look. */
    public boolean urgent() {
        return !emails.isEmpty();
    }

    public List<String> emailList() {
        return List.copyOf(emails);
    }
}
