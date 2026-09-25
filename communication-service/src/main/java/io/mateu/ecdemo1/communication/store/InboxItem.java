package io.mateu.ecdemo1.communication.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Something waiting for a person, in the inbox of the roles that see to it: a notification with the
 * link to the screen that resolves it, or a task of the forms engine. It stays open until what it is
 * about is resolved — the cause, the gate, the task — and then leaves every inbox at once.
 */
@Entity
@Table(name = "inbox_item", indexes = {
        @Index(name = "inbox_item_subject", columnList = "subject"),
        @Index(name = "inbox_item_open", columnList = "resolvedAt")})
public class InboxItem {

    /** An ACTION asks a person to do something on a screen; a TASK is a form to fill in. */
    public enum Kind { ACTION, TASK }

    @Id
    public String id;
    @Column(length = 20)
    public String kind;
    /** The notification's type, or TASK. */
    @Column(length = 60)
    public String type;
    public String hotelCode;
    /** What resolving closes it: a cause key, integration/HOTEL, task/ID. */
    @Column(length = 500)
    public String subject;
    @Column(length = 500)
    public String title;
    @Column(length = 4000)
    public String body;
    @Column(length = 1000)
    public String link;
    /** The roles whose inbox shows it, comma-separated; "*" is everyone's. */
    @Column(length = 500)
    public String roles;
    public boolean urgent;
    public Instant createdAt;
    public Instant resolvedAt;
    public String resolvedBy;
    /** When it was handed to the chat spaces and the browsers of its roles; null, not yet. */
    public Instant announcedAt;

    public boolean isOpen() {
        return resolvedAt == null;
    }
}
