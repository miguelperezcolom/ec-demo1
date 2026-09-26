package io.mateu.ecdemo1.communication.routing;

import io.mateu.ecdemo1.communication.store.Channel;
import io.mateu.ecdemo1.communication.store.Recipient;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientDefaultsTest {

    @Test
    void anEmptyTableIsSeededWithWhatUsedToBeConfigured() {
        var seeded = RecipientDefaults.bringOver(List.of(), "admins@example.com");

        assertThat(seeded).extracting(r -> r.name)
                .containsExactly("Integration administrators", "Google Chat", "Urgent, by e-mail");
        var admins = seeded.get(0);
        assertThat(admins.roleList()).containsExactly("ai-admin");
        assertThat(admins.channelSet()).containsExactlyInAnyOrder(Channel.INBOX, Channel.WEB_PUSH);
        assertThat(admins.typeList()).isEmpty();
        assertThat(admins.tasks).isFalse();
        var chat = seeded.get(1);
        assertThat(chat.channelSet()).containsExactly(Channel.GOOGLE_CHAT);
        assertThat(chat.spaces(2)).containsExactly(1, 2);
        assertThat(chat.tasks).isTrue();
        var urgent = seeded.get(2);
        assertThat(urgent.email).isEqualTo("admins@example.com");
        assertThat(urgent.typeList()).containsExactly("PMS_REJECTED", "RETRYING_TOO_LONG");
        assertThat(seeded).allMatch(r -> r.active && r.id.startsWith("default-"));

        // Together they reproduce what the configuration used to do.
        var cause = Plan.of(seeded, "CAUSE_OPENED", "MRU01", 2);
        assertThat(cause.inboxRoles()).containsExactly("ai-admin");
        assertThat(cause.pushRoles()).containsExactly("ai-admin");
        assertThat(cause.spaces()).containsExactly(1, 2);
        assertThat(cause.urgent()).isFalse();
        assertThat(Plan.of(seeded, "PMS_REJECTED", "MRU01", 2).emails()).containsExactly("admins@example.com");
    }

    @Test
    void theRecipientsOfBeforeAreBroughtOverAsTheEmailTheyWereAndTheRestIsSeeded() {
        var anyType = new Recipient();
        anyType.name = "Integration administrators";
        anyType.email = "admins@example.com";
        anyType.active = true;
        var oneType = new Recipient();
        oneType.name = "Palma";
        oneType.email = "palma@example.com";
        oneType.legacyType = "PMS_REJECTED";
        oneType.hotelCode = "PMI01";
        oneType.active = true;

        var changed = RecipientDefaults.bringOver(List.of(anyType, oneType), "admins@example.com");

        assertThat(anyType.channelSet()).containsExactly(Channel.EMAIL);
        assertThat(anyType.typeList()).containsExactly("PMS_REJECTED", "RETRYING_TOO_LONG");
        assertThat(oneType.typeList()).containsExactly("PMS_REJECTED");
        assertThat(oneType.legacyType).isNull();
        // Their e-mail is already there: no second urgent recipient.
        assertThat(changed).extracting(r -> r.name)
                .containsExactly("Integration administrators", "Palma", "Integration administrators", "Google Chat");
    }

    @Test
    void aTableWithRecipientsOfTheNewKindIsLeftAsPeopleDecided() {
        var r = new Recipient();
        r.name = "Only the front desk";
        r.channels = Recipient.join(EnumSet.of(Channel.INBOX));
        r.roles = "front-desk";
        r.active = true;

        assertThat(RecipientDefaults.bringOver(List.of(r), "admins@example.com")).isEmpty();
    }

    @Test
    void withoutADefaultAddressNoEmailRecipientIsSeeded() {
        assertThat(RecipientDefaults.bringOver(List.of(), " ")).extracting(r -> r.name)
                .containsExactly("Integration administrators", "Google Chat");
    }
}
