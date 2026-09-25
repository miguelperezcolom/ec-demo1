package io.mateu.ecdemo1.crsintegration.inbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/** An event a consumer has already handled. */
@Entity
@Table(name = "inbox_entry")
@IdClass(InboxEntry.Key.class)
@NoArgsConstructor
public class InboxEntry {

    @Id
    String consumer;
    @Id
    String eventId;
    Instant receivedAt;

    public record Key(String consumer, String eventId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
