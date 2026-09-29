package io.mateu.ecdemo1.frontoffice.domain.stay;

import java.time.Instant;
import java.util.Optional;

/**
 * What the desk said it read of a stay's warnings: at check-in its blocking notices, at check-out its
 * notices and its kárdex still pending or rejected by Salesforce. One per stay and moment — the last —
 * with what it covered: warnings that changed after it are not acknowledged.
 */
public interface NoticeAcknowledgements {

  enum Moment { CHECK_IN, CHECK_OUT }

  /** @param fingerprint what was read, as the warnings' fingerprint said it then */
  record Acknowledgement(String stayId, Moment moment, String fingerprint, String by, Instant at) {}

  Optional<Acknowledgement> of(String stayId, Moment moment);

  void save(Acknowledgement acknowledgement);
}
