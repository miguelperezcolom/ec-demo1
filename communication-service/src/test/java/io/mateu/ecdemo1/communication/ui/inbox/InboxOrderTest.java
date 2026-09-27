package io.mateu.ecdemo1.communication.ui.inbox;

import io.mateu.ecdemo1.communication.store.InboxItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InboxOrderTest {

    static InboxItem item(String id, String created) {
        var i = new InboxItem();
        i.id = id;
        i.createdAt = Instant.parse(created);
        return i;
    }

    @Test
    void unseenOldestFirstThenSeenMostRecentlySeenFirst() {
        var items = List.of(
                item("new-late", "2026-09-26T10:00:00Z"),
                item("seen-long-ago", "2026-09-20T10:00:00Z"),
                item("new-early", "2026-09-25T10:00:00Z"),
                item("seen-just-now", "2026-09-21T10:00:00Z"));
        var seenAt = Map.of(
                "seen-long-ago", Instant.parse("2026-09-22T10:00:00Z"),
                "seen-just-now", Instant.parse("2026-09-26T09:00:00Z"));

        var ids = items.stream().sorted(InboxPage.order(seenAt)).map(i -> i.id).toList();

        assertThat(ids).containsExactly("new-early", "new-late", "seen-just-now", "seen-long-ago");
    }
}
