package io.mateu.ecdemo1.frontoffice.domain.stay;

import java.time.Duration;
import java.time.Instant;

/**
 * A check-in the desk forced with steps missing — who, when and why, and what was missing then. The
 * stay is in the house but its check-in is incomplete until the steps are done (completed), and it
 * cannot check out meanwhile. Reception is told once ({@code overdueNotifiedAt}) when a document is
 * still missing past the deadline of the traveller's registration (24 h from the arrival in Spain).
 */
public record ForcedCheckIn(
    String stayId,
    String forcedBy,
    Instant forcedAt,
    String reason,
    String missingWhenForced,
    String completedBy,
    Instant completedAt,
    Instant overdueNotifiedAt) {

  public ForcedCheckIn {
    if (stayId == null || stayId.isBlank()) throw new IllegalArgumentException("Stay id is required");
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A forced check-in needs a reason");
    if (forcedAt == null) throw new IllegalArgumentException("When it was forced is required");
  }

  public static ForcedCheckIn forced(String stayId, String by, Instant at, String reason, String missing) {
    return new ForcedCheckIn(stayId, by, at, reason.trim(), missing, null, null, null);
  }

  /** Whether its steps are still to be completed. */
  public boolean open() {
    return completedAt == null;
  }

  /** When the documents must be in: the arrival (the forced check-in) plus {@code deadline}. */
  public Instant documentsDue(Duration deadline) {
    return forcedAt.plus(deadline);
  }

  public ForcedCheckIn completed(String by, Instant at) {
    return new ForcedCheckIn(stayId, forcedBy, forcedAt, reason, missingWhenForced, by, at, overdueNotifiedAt);
  }

  public ForcedCheckIn overdueNotified(Instant at) {
    return new ForcedCheckIn(stayId, forcedBy, forcedAt, reason, missingWhenForced, completedBy, completedAt, at);
  }
}
