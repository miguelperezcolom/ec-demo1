package io.mateu.ecdemo1.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * {@code messaging.*}: which tables the outbox and the inbox live in, and how the relay goes. The
 * defaults are what the services' own copies did, and their tables' names — a service switching to
 * this library keeps its rows.
 *
 * @param outbox       the outbox and its relay
 * @param inbox        the inbox
 * @param createSchema whether to create the tables (and add the columns this library needs to an
 *                     existing one) at startup; additive only, never drops or renames anything
 */
@ConfigurationProperties("messaging")
public record MessagingProperties(Outbox outbox, Inbox inbox, Boolean createSchema) {

    static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    public MessagingProperties {
        if (outbox == null) outbox = new Outbox(null, null, null, 0, null, null, 0, null, null, null, null);
        if (inbox == null) inbox = new Inbox(null);
        if (createSchema == null) createSchema = true;
    }

    public static MessagingProperties defaults() {
        return new MessagingProperties(null, null, null);
    }

    /**
     * @param table                 the outbox table
     * @param relay                 whether this instance relays (false: messages only accumulate)
     * @param interval              pause between relay passes
     * @param batchSize             messages taken per pass
     * @param retention             how long a relayed message is kept before cleanup deletes it
     * @param cleanupInterval       how often cleanup runs
     * @param maxAttempts           failed sends before a message is abandoned (kept, never retried);
     *                              0 = retried for ever
     * @param backoffInitial        wait after a message's first failed send, doubled on each further one
     * @param backoffMax            the longest that wait gets
     * @param continueAfterFailure  false: a failed send ends the pass (a broker that is down costs one
     *                              timeout per pass, not one per message); true: the pass goes on with
     *                              the other keys (a destination that fails per message, as an HTTP API)
     * @param sendTimeout           how long a transport waits for the destination to take a message
     */
    public record Outbox(String table, Boolean relay, Duration interval, int batchSize, Duration retention,
                         Duration cleanupInterval, int maxAttempts, Duration backoffInitial, Duration backoffMax,
                         Boolean continueAfterFailure, Duration sendTimeout) {
        public Outbox {
            table = checked(table == null ? "outbox_message" : table);
            if (relay == null) relay = true;
            if (interval == null) interval = Duration.ofMillis(500);
            if (batchSize <= 0) batchSize = 100;
            if (retention == null) retention = Duration.ofDays(7);
            if (cleanupInterval == null) cleanupInterval = Duration.ofHours(1);
            if (maxAttempts < 0) maxAttempts = 0;
            if (backoffInitial == null) backoffInitial = Duration.ofSeconds(1);
            if (backoffMax == null) backoffMax = Duration.ofMinutes(1);
            if (continueAfterFailure == null) continueAfterFailure = false;
            if (sendTimeout == null) sendTimeout = Duration.ofSeconds(15);
        }

        /** The wait before the next try of a message that has failed {@code attempts} times. */
        public Duration backoff(int attempts) {
            var wait = backoffInitial;
            for (int i = 1; i < attempts && wait.compareTo(backoffMax) < 0; i++) {
                wait = wait.multipliedBy(2);
            }
            return wait.compareTo(backoffMax) > 0 ? backoffMax : wait;
        }
    }

    /** @param table the inbox table */
    public record Inbox(String table) {
        public Inbox {
            table = checked(table == null ? "inbox_entry" : table);
        }
    }

    /** Table names go into SQL as they are: only identifiers, optionally schema-qualified. */
    static String checked(String table) {
        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Not a table name: " + table);
        }
        return table;
    }
}
