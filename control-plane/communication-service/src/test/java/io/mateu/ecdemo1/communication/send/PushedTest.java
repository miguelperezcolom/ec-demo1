package io.mateu.ecdemo1.communication.send;

import io.mateu.ecdemo1.communication.routing.Plan;
import io.mateu.ecdemo1.communication.store.InboxItem;
import io.mateu.ecdemo1.communication.store.PushSubscription;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PushedTest {

    static PushSubscription browser(String username, String roles) {
        var s = new PushSubscription();
        s.username = username;
        s.roles = roles;
        return s;
    }

    @Test
    void aNotificationIsPushedToThePeopleOfTheRecipientsThatPushIt() {
        var item = new InboxItem();
        item.kind = InboxItem.Kind.ACTION.name();
        item.roles = "front-desk";
        var plan = new Plan(Set.of(), Set.of("front-desk"), Set.of("ana"), Set.of("ai-admin"), Set.of(), Set.of(), true);

        assertThat(Announcements.pushed(item, plan, browser("ana", "front-desk"))).isTrue();
        assertThat(Announcements.pushed(item, plan, browser("pepe", "ai-admin,offline_access"))).isTrue();
        // In the front desk's inbox, but nobody pushes it to them.
        assertThat(Announcements.pushed(item, plan, browser("luis", "front-desk"))).isFalse();
    }

    @Test
    void aBrowserAtTheFrontDeskIsToldOnlyWhatIsPushedToTheDesk() {
        var item = new InboxItem();
        item.kind = InboxItem.Kind.ACTION.name();
        var desk = browser("demo", "ai-admin,user");
        desk.app = PushSubscription.FRONT_DESK;
        // The consoles' recipients push to ai-admin: the same person's browser at the desk is not told.
        var consoles = new Plan(Set.of(), Set.of(), Set.of("demo"), Set.of("ai-admin"), Set.of(), Set.of(), true);
        assertThat(Announcements.pushed(item, consoles, desk)).isFalse();
        assertThat(Announcements.pushed(item, consoles, browser("demo", "ai-admin,user"))).isTrue();

        var toTheDesk = new Plan(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), true, Set.of(), Set.of("user"));
        assertThat(Announcements.pushed(item, toTheDesk, desk)).isTrue();
        assertThat(Announcements.pushed(item, toTheDesk, browser("demo", "ai-admin,user"))).isFalse();

        // Nor a task: the desk has no inbox.
        var task = new InboxItem();
        task.kind = InboxItem.Kind.TASK.name();
        task.roles = "user";
        var nobody = new Plan(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);
        assertThat(Announcements.pushed(task, nobody, desk)).isFalse();
    }

    @Test
    void aTaskIsPushedToThePeopleWhoseInboxItIsIn() {
        var task = new InboxItem();
        task.kind = InboxItem.Kind.TASK.name();
        task.roles = "finance";
        var nobody = new Plan(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);

        assertThat(Announcements.pushed(task, nobody, browser("luis", "finance"))).isTrue();
        assertThat(Announcements.pushed(task, nobody, browser("pepe", "ai-admin"))).isFalse();
    }
}
