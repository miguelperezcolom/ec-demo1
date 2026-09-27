package io.mateu.ecdemo1.messaging;

import java.time.Instant;
import java.util.Map;

/**
 * One row of the outbox, as the relay hands it to a transport and as a test reads it back.
 *
 * @param seq         the order it was written in, which is the order it is sent in (per key)
 * @param destination where it goes: a Spring Cloud Stream binding, a Kafka topic, whatever the
 *                    service's transport understands (the {@code binding} column)
 * @param key         what keeps messages in order: those of one key are sent one after another, and a
 *                    failed one holds the rest of its key back; null for none
 * @param type        what it is, for the log and the reader (the {@code event_type} column)
 * @param payload     the message, as written (JSON, usually)
 * @param headers     the headers given when it was appended, to go on the record
 * @param traceparent the W3C trace it was written in; null when nothing was traced
 * @param tracestate  its W3C trace state
 * @param attempts    failed sends so far
 * @param createdAt   when it was written
 * @param publishedAt when it was sent; null while pending
 */
public record OutboxMessage(long seq, String destination, String key, String type, String payload,
                            Map<String, String> headers, String traceparent, String tracestate, int attempts,
                            Instant createdAt, Instant publishedAt) {
}
