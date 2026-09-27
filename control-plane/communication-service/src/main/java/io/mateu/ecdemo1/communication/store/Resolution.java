package io.mateu.ecdemo1.communication.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

/**
 * The last time a subject was resolved. Resolutions and notifications travel on different topics, so
 * one may overtake the other: a notification that arrives after the resolution of what it announced,
 * and was requested before it, is already resolved.
 */
@Entity
public class Resolution {

    @Id
    @Column(length = 500)
    public String subject;
    public Instant resolvedAt;
    public String resolvedBy;
}
