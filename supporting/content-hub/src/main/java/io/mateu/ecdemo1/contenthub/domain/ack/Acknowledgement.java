package io.mateu.ecdemo1.contenthub.domain.ack;

import java.time.Instant;

/**
 * That an employee read, or confirmed having read, a version of an item. Keyed by (item, version,
 * employee, type) and idempotent: thousands of them for the same item never compete with the item.
 */
public record Acknowledgement(String itemId, int version, String userId, Type type, Instant at) {

    public enum Type { READ, CONFIRMATION }
}
