package io.mateu.ecdemo1.mapping.outbox;

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
 * A message waiting to be published, or already published. {@code seq} is the order they were
 * written in, which is the order they are published in.
 */
@Entity
@Table(name = "outbox_message")
@NoArgsConstructor
@Getter
@Setter
public class OutboxMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long seq;

    /** The Spring Cloud Stream binding it is published through. */
    @Column(nullable = false)
    String binding;

    /** The Kafka key, so the messages of one aggregate stay in order; null for none. */
    String messageKey;

    @Column(nullable = false)
    String eventType;

    @Column(nullable = false, columnDefinition = "text")
    String payload;

    @Column(nullable = false)
    Instant createdAt;

    Instant publishedAt;

}
