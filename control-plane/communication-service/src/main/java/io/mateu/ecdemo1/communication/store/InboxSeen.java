package io.mateu.ecdemo1.communication.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * That one person has seen an inbox item. Per person and not on the item, because an inbox is a
 * role's: one of its people having looked says nothing about whether the others have. Seeing does
 * not resolve anything — the item stays in every inbox until what it is about is resolved.
 */
@Entity
@Table(name = "inbox_seen", indexes = @Index(name = "inbox_seen_username", columnList = "username"))
public class InboxSeen {

    /** itemId + "|" + username. */
    @Id
    @Column(length = 1000)
    public String id;
    @Column(length = 500)
    public String itemId;
    public String username;
    public Instant seenAt;

    public static String id(String itemId, String username) {
        return itemId + "|" + username;
    }
}
