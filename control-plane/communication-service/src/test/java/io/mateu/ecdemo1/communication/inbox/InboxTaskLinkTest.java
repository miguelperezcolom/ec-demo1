package io.mateu.ecdemo1.communication.inbox;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.store.InboxItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Where a task's inbox item leads: the forms engine's page for that one task. */
class InboxTaskLinkTest {

    static Inbox inbox(String taskLink) {
        var properties = new CommunicationProperties(null, null, 0,
                new CommunicationProperties.Inbox(taskLink, null), null, null);
        return new Inbox(null, null, null, null, properties, null);
    }

    static InboxItem item(String id, InboxItem.Kind kind, String link) {
        var item = new InboxItem();
        item.id = id;
        item.kind = kind.name();
        item.link = link;
        return item;
    }

    @Test
    void byDefaultATaskLeadsToItsOwnPage() {
        assertThat(inbox(null).linkOf(item("task/977984b8", InboxItem.Kind.TASK, null)))
                .isEqualTo("/forms/task/977984b8");
        assertThat(inbox("").linkOf(item("task/t-1", InboxItem.Kind.TASK, null))).isEqualTo("/forms/task/t-1");
    }

    @Test
    void aTaskWrittenWhenItsLinkWasTheListLeadsToItsPageAllTheSame() {
        // The reset's confirmation waited in the inbox with /forms/tasks: the row lives as long as the
        // task does, so the link is worked out from the task rather than read from the row.
        assertThat(inbox(null).linkOf(item("task/977984b8", InboxItem.Kind.TASK, "/forms/tasks")))
                .isEqualTo("/forms/task/977984b8");
    }

    @Test
    void aConfiguredLinkNamesTheTaskWhereItSaysSo() {
        assertThat(inbox("https://console.ec1.mateu.io/forms/task/{taskId}").linkOf(item("task/t-1", InboxItem.Kind.TASK, null)))
                .isEqualTo("https://console.ec1.mateu.io/forms/task/t-1");
        assertThat(inbox("/forms/tasks").linkOf(item("task/t-1", InboxItem.Kind.TASK, null))).isEqualTo("/forms/tasks");
    }

    @Test
    void aNotificationKeepsTheLinkItWasSentWith() {
        assertThat(inbox(null).linkOf(item("n-1", InboxItem.Kind.ACTION, "https://console/mapping/causes")))
                .isEqualTo("https://console/mapping/causes");
        assertThat(inbox(null).linkOf(null)).isNull();
    }
}
