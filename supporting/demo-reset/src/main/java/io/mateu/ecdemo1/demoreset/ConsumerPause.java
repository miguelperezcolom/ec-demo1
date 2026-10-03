package io.mateu.ecdemo1.demoreset;

/**
 * Something that feeds a service while it runs — a Kafka consumer, a subscription — held still while
 * the service empties its tables, so nothing it receives lands half-way through. Every bean of this
 * type is paused before the reset and resumed after it, whatever happened.
 */
public interface ConsumerPause {

    /** Stops taking new work; returns once what was in hand is done, or as soon as it can tell. */
    void pause();

    void resume();

    /** For the logs. */
    default String describe() {
        return getClass().getSimpleName();
    }
}
