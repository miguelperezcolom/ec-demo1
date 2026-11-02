package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.health.Finding;
import io.mateu.ecdemo1.contenthub.domain.health.LinkStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * «Salud del contenido»'s results: the runs, the current findings (replaced by each run, keeping when each
 * was first seen), the «revisado» marks and what external URLs last answered.
 */
public interface HealthRepository {

    /** One run of the checks. {@code counts} by check name; {@code error} when it failed. */
    record Run(String id, String trigger, String by, Instant startedAt, Instant finishedAt, Map<String, Integer> counts,
               String error) {
    }

    /** A finding as stored: since when it is there. */
    record StoredFinding(Finding finding, Instant firstSeen, Instant lastSeen) {
    }

    /** «Marcar como revisado»: the finding is left out of the counts until {@code until}. */
    record Snooze(String key, Instant until, String by, String note, Instant at) {
    }

    /**
     * Claims the night's run: true only for the first caller for that day, whatever the number of
     * replicas, restarts or timer ticks. The run is started with it.
     */
    boolean claimNightly(LocalDate day, String runId, Instant now);

    void startRun(String runId, String trigger, String by, Instant now);

    void finishRun(String runId, Map<String, Integer> counts, String error, Instant now);

    Optional<Run> lastRun();

    /** The findings become exactly these: new ones added, gone ones removed, the rest keep their first-seen. */
    void replaceFindings(List<Finding> findings, Instant now);

    List<StoredFinding> findings();

    Optional<StoredFinding> finding(String key);

    /** A finding fixed from the screen, gone before the next run notices. */
    void removeFinding(String key);

    Map<String, Snooze> snoozes();

    void snooze(Snooze snooze);

    void unsnooze(String key);

    Optional<LinkStatus> cachedLink(String url);

    void cacheLink(LinkStatus status);
}
