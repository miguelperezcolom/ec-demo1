package io.mateu.ecdemo1.mdm.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Where reading Salesforce left off: the Pub/Sub replay id of the last event handled, and how far
 * the poll has looked. After a restart both resume from here instead of from now.
 */
@Entity
@Table(name = "salesforce_cursor")
@NoArgsConstructor
public class Cursor {

    public static final String PUBSUB = "pubsub";
    public static final String POLL = "poll";

    @Id
    public String name;
    /** Base64 of the replay id. */
    @Column(length = 200)
    public String replayId;
    public Instant until;
}
