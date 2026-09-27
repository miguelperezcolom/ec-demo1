package io.mateu.ecdemo1.messaging;

import java.util.Map;

/**
 * How the relay sends a message where it goes. Returns only once the destination has it — a Kafka
 * broker's acknowledgement, an API's 2xx — and throws otherwise: the message is then tried again after
 * a backoff, and the messages of its key wait for it.
 *
 * <p>A service has one: {@link io.mateu.ecdemo1.messaging.transport.StreamBridgeTransport} is the
 * default where Spring Cloud Stream is; a service declares its own bean otherwise (a
 * {@link io.mateu.ecdemo1.messaging.transport.KafkaTemplateTransport}, or one that calls an API). With
 * none, nothing is relayed and the messages wait in the outbox.
 */
@FunctionalInterface
public interface OutboxTransport {

    /**
     * @param message the message
     * @param headers what goes on the record: the headers it was appended with and the trace context,
     *                as UTF-8 bytes (what W3C propagators on the consuming side read)
     */
    void send(OutboxMessage message, Map<String, byte[]> headers) throws Exception;
}
