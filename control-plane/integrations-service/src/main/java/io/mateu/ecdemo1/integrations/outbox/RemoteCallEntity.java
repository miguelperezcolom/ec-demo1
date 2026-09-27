package io.mateu.ecdemo1.integrations.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A {@link RemoteCall} waiting to be sent, sent ({@code doneAt}), or given up on ({@code failedAt}).
 * {@code claimedUntil} keeps two senders — right after the commit, and the relay — from sending it
 * at once.
 */
@Entity
@Table(name = "remote_call")
@NoArgsConstructor
@Getter
@Setter
public class RemoteCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String kind;

    @Column(nullable = false, columnDefinition = "text")
    String payload;

    @Column(nullable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant nextAttemptAt;

    int attempts;

    Instant claimedUntil;

    @Column(length = 2000)
    String lastError;

    Instant doneAt;

    Instant failedAt;
}
