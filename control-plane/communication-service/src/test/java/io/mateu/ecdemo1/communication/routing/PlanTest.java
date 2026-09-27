package io.mateu.ecdemo1.communication.routing;

import io.mateu.ecdemo1.communication.store.Channel;
import io.mateu.ecdemo1.communication.store.Recipient;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest {

    static Recipient recipient(EnumSet<Channel> channels, String users, String roles, String email, String types, String hotel) {
        var r = new Recipient();
        r.active = true;
        r.channels = Recipient.join(channels);
        r.users = users;
        r.roles = roles;
        r.email = email;
        r.types = types;
        r.hotelCode = hotel;
        return r;
    }

    @Test
    void eachChannelReachesItsOwnPeople() {
        var admins = recipient(EnumSet.of(Channel.INBOX, Channel.WEB_PUSH), null, "ai-admin", null, null, null);
        var ana = recipient(EnumSet.of(Channel.WEB_PUSH), "ana", null, null, null, null);
        var urgent = recipient(EnumSet.of(Channel.EMAIL), null, null, "ops@example.com", "PMS_REJECTED", null);
        var chat = recipient(EnumSet.of(Channel.GOOGLE_CHAT), null, null, null, null, null);
        chat.chatSpaces = "2";

        var plan = Plan.of(List.of(admins, ana, urgent, chat), "CAUSE_OPENED", "MRU01", 2);

        assertThat(plan.inboxRoles()).containsExactly("ai-admin");
        assertThat(plan.inboxUsers()).isEmpty();
        assertThat(plan.pushRoles()).containsExactly("ai-admin");
        assertThat(plan.pushUsers()).containsExactly("ana");
        // Not urgent: the e-mail recipient only wants the PMS's rejections.
        assertThat(plan.emails()).isEmpty();
        assertThat(plan.urgent()).isFalse();
        assertThat(plan.spaces()).containsExactly(2);
        assertThat(plan.anyone()).isTrue();

        var rejected = Plan.of(List.of(admins, ana, urgent, chat), "PMS_REJECTED", "MRU01", 2);
        assertThat(rejected.emails()).containsExactly("ops@example.com");
        assertThat(rejected.urgent()).isTrue();
    }

    @Test
    void anItemGoesOncePerPersonAddressAndSpaceHoweverManyRecipientsAskForIt() {
        var one = recipient(EnumSet.of(Channel.INBOX, Channel.EMAIL, Channel.GOOGLE_CHAT), "ana", "ai-admin", "ops@example.com", null, null);
        var two = recipient(EnumSet.of(Channel.INBOX, Channel.EMAIL, Channel.GOOGLE_CHAT), "ana", "ai-admin", " ops@example.com ", null, "MRU01");

        var plan = Plan.of(List.of(one, two), "CAUSE_OPENED", "MRU01", 2);

        assertThat(plan.inboxUsers()).containsExactly("ana");
        assertThat(plan.inboxRoles()).containsExactly("ai-admin");
        assertThat(plan.emailList()).containsExactly("ops@example.com");
        assertThat(plan.spaces()).containsExactly(1, 2);
    }

    @Test
    void nobodyWantsItAndTheGapShows() {
        var palma = recipient(EnumSet.of(Channel.INBOX), null, "front-desk", null, null, "PMI01");
        var inactive = recipient(EnumSet.of(Channel.EMAIL), null, null, "x@example.com", null, null);
        inactive.active = false;

        var plan = Plan.of(List.of(palma, inactive), "CAUSE_OPENED", "MRU01", 2);

        assertThat(plan.anyone()).isFalse();
        assertThat(plan.inboxRoles()).isEmpty();
        assertThat(plan.emails()).isEmpty();
    }

    @Test
    void anEmailRecipientWithoutAnAddressEmailsNobody() {
        var r = recipient(EnumSet.of(Channel.EMAIL), null, null, " ", null, null);
        assertThat(Plan.of(List.of(r), "PMS_REJECTED", "MRU01", 2).emails()).isEmpty();
    }

    @Test
    void aTaskReachesOnlyTheRecipientsThatWantTasks() {
        var chat = recipient(EnumSet.of(Channel.GOOGLE_CHAT), null, null, null, null, null);
        var admins = recipient(EnumSet.of(Channel.INBOX), null, "ai-admin", null, null, null);
        assertThat(Plan.of(List.of(chat, admins), Recipient.TASK, null, 2).anyone()).isFalse();
        chat.tasks = true;
        var plan = Plan.of(List.of(chat, admins), Recipient.TASK, null, 2);
        assertThat(plan.spaces()).containsExactly(1, 2);
        assertThat(plan.inboxRoles()).isEmpty();
    }
}
