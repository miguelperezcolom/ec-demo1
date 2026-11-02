package io.mateu.ecdemo1.contenthub.application.out;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * What the intranet has told its readers: the fan-out jobs still to do, and a row per item version,
 * reader, kind and channel. That row is the idempotency: whoever manages to write it is who sends.
 */
public interface NotificationLog {

    enum Kind { PUBLISHED, CONFIRMATION_DUE }

    enum Channel { IN_APP, EMAIL }

    /** An item version whose readers are still to be told. */
    record Job(String itemId, int version, int attempts) {
    }

    /** A notification still open in a reader's inbox. */
    record Open(String itemId, String userId, Kind kind) {
    }

    /** @return whether it was new: a version gets one job, however many times it is asked for */
    boolean enqueue(String itemId, int version, Instant at);

    List<Job> pendingJobs(int limit);

    void attempted(Job job);

    void finish(Job job, String outcome, Instant at);

    Set<String> sentTo(String itemId, int version, Kind kind, Channel channel);

    /** @return whether this reader had not been told yet; only then does the caller send */
    boolean markSent(String itemId, int version, String userId, Kind kind, Channel channel, Instant at);

    /** The readers told in the app about this item, in any version, for the item's own figures. */
    long readersTold(String itemId);

    List<Open> openFor(String userId, String itemId, Kind kind);

    /** The items with something open in somebody's inbox. */
    Set<String> itemsWithOpen();

    List<Open> openOf(String itemId, int limit);

    List<Open> openOlderThan(Instant at, int limit);

    void resolved(Open open, Instant at);
}
