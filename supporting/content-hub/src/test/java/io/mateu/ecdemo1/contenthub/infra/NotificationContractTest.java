package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.out.NotificationLog;
import io.mateu.ecdemo1.contenthub.application.out.NotificationSender;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.out.messaging.OutboxNotificationSender;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.messaging.Outbox;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What goes on the platform's topics, as the outbox really gets it, against each topic's schema
 * (contracts/schemas) — and the item's flags, as documents saved before they existed read them.
 */
class NotificationContractTest {

    record Written(String destination, String key, String payload) {
    }

    final List<Written> written = new ArrayList<>();
    final Outbox outbox = new Outbox(null, null, null, null) {
        @Override
        public void append(String destination, String key, String type, String payload, Map<String, String> headers) {
            written.add(new Written(destination, key, payload));
        }
    };
    final OutboxNotificationSender sender = new OutboxNotificationSender(outbox);

    static final Instant AT = Instant.parse("2026-11-12T09:30:00Z");

    @Test
    void a_readers_notification_is_what_the_notifications_schema_says() {
        for (var kind : NotificationLog.Kind.values()) {
            sender.send(new NotificationSender.ReaderNotification(kind, "turnos", 1, "ana", "Turnos", "Los de noviembre",
                    "/intranet/noticias/turnos", AT));
        }

        assertThat(written).hasSize(2).allSatisfy(w -> {
            assertThat(w.destination()).isEqualTo("notifications");
            Contracts.topic("notifications").assertValid(w.payload());
            assertThat(w.payload()).contains("\"addressees\":[\"ana\"]").contains("\"hotelCode\":null");
        });
        assertThat(written.getFirst().key()).isEqualTo("intranet:published:turnos:v1:ana");
        assertThat(written.getFirst().payload()).contains("INTRANET_PUBLISHED");
        assertThat(written.getLast().payload()).contains("INTRANET_CONFIRMATION_DUE");
    }

    @Test
    void closing_one_is_what_the_resolutions_schema_says() {
        sender.resolve(NotificationSender.subject(NotificationLog.Kind.PUBLISHED, "turnos", "ana"), "ana", AT);

        assertThat(written).singleElement().satisfies(w -> {
            assertThat(w.destination()).isEqualTo("notification-resolutions");
            assertThat(w.key()).isEqualTo("intranet/item/turnos/ana");
            Contracts.topic("notification-resolutions").assertValid(w.payload());
        });
    }

    @Test
    void an_item_saved_before_the_flags_existed_goes_by_its_type() {
        var old = Json.read("{\"translations\":{},\"readTracking\":false,\"confirmationRequired\":false}", ItemContent.class);

        assertThat(old.notifyReaders()).isNull();
        assertThat(old.important()).isFalse();
        assertThat(old.notifies(ItemType.NOTICE)).isTrue();
        assertThat(old.notifies(ItemType.ARTICLE)).isFalse();
    }

    @Test
    void the_flags_survive_being_saved() {
        var c = new ItemContent(null, null, null, null, null, null, false, false, null, null).withNotification(false, true);

        var back = Json.read(Json.write(c), ItemContent.class);

        assertThat(back.notifyReaders()).isFalse();
        assertThat(back.important()).isTrue();
    }
}
