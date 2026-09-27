package io.mateu.ecdemo1.communication.routing;

import io.mateu.ecdemo1.communication.inbox.Inbox;
import io.mateu.ecdemo1.communication.store.InboxItem;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceTest {

    @Test
    void anItemIsInTheInboxOfItsPeopleByNameOrByRole() {
        var item = new InboxItem();
        item.roles = "ai-admin";
        item.users = "ana";

        assertThat(Inbox.visibleTo(item, Set.of("ai-admin"), "pepe")).isTrue();
        assertThat(Inbox.visibleTo(item, Set.of("front-desk"), "ana")).isTrue();
        assertThat(Inbox.visibleTo(item, Set.of("front-desk"), "pepe")).isFalse();
        assertThat(Inbox.visibleTo(item, Set.of(), null)).isFalse();

        item.roles = Inbox.EVERYONE;
        assertThat(Inbox.visibleTo(item, Set.of(), null)).isTrue();
    }

    @Test
    void nobodysItemIsInNobodysInbox() {
        var item = new InboxItem();
        assertThat(Inbox.visibleTo(item, Set.of("ai-admin"), "ana")).isFalse();
    }
}
