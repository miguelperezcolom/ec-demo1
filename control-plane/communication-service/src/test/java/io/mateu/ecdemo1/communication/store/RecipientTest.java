package io.mateu.ecdemo1.communication.store;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientTest {

    @Test
    void aRecipientWithoutChannelsIsToldByEmailAsBefore() {
        var r = new Recipient();
        assertThat(r.channelSet()).containsExactly(Channel.EMAIL);
        r.channels = " ";
        assertThat(r.by(Channel.EMAIL)).isTrue();
        assertThat(r.by(Channel.WEB_PUSH)).isFalse();
    }

    @Test
    void itsChannelsAreWhatItNames() {
        var r = new Recipient();
        r.channels = Recipient.join(EnumSet.of(Channel.GOOGLE_CHAT, Channel.WEB_PUSH));
        assertThat(r.channels).isEqualTo("WEB_PUSH,GOOGLE_CHAT");
        assertThat(r.channelSet()).containsExactlyInAnyOrder(Channel.GOOGLE_CHAT, Channel.WEB_PUSH);
        assertThat(r.by(Channel.EMAIL)).isFalse();
    }

    @Test
    void noSpaceNamedIsEverySpaceAndOnlySpacesThatExistCount() {
        var r = new Recipient();
        assertThat(r.spaces(2)).containsExactly(1, 2);
        r.chatSpaces = "2, 5, x";
        assertThat(r.spaces(2)).containsExactly(2);
        r.chatSpaces = "5";
        assertThat(r.spaces(2)).isEmpty();
    }

    @Test
    void itsUsersAndRolesAreCommaSeparatedLists() {
        var r = new Recipient();
        assertThat(r.userList()).isEmpty();
        r.users = "ana, luis,,ana";
        r.roles = "ai-admin";
        assertThat(r.userList()).containsExactly("ana", "luis");
        assertThat(r.is("luis", Set.of())).isTrue();
        assertThat(r.is("pepe", Set.of("front-desk", "ai-admin"))).isTrue();
        assertThat(r.is("pepe", Set.of("front-desk"))).isFalse();
        assertThat(r.is(null, null)).isFalse();
    }

    @Test
    void noTypeNamedIsEveryTypeAndTasksOnlyWhenAskedFor() {
        var r = new Recipient();
        r.active = true;
        assertThat(r.wants("CAUSE_OPENED", "MRU01")).isTrue();
        assertThat(r.wants(Recipient.TASK, null)).isFalse();
        r.tasks = true;
        assertThat(r.wants(Recipient.TASK, null)).isTrue();
        r.types = Recipient.join(List.of("PMS_REJECTED", "RETRYING_TOO_LONG"));
        assertThat(r.wants("PMS_REJECTED", "MRU01")).isTrue();
        assertThat(r.wants("CAUSE_OPENED", "MRU01")).isFalse();
    }

    @Test
    void aHotelsRecipientWantsOnlyItsHotelAndAnInactiveOneNothing() {
        var r = new Recipient();
        r.active = true;
        r.hotelCode = "PMI01";
        r.tasks = true;
        assertThat(r.wants("CAUSE_OPENED", "PMI01")).isTrue();
        assertThat(r.wants("CAUSE_OPENED", "MRU01")).isFalse();
        // A task is no hotel's.
        assertThat(r.wants(Recipient.TASK, null)).isFalse();
        r.hotelCode = null;
        r.active = false;
        assertThat(r.wants("CAUSE_OPENED", "PMI01")).isFalse();
    }
}
