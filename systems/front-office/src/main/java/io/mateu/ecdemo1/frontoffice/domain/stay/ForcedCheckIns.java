package io.mateu.ecdemo1.frontoffice.domain.stay;

import java.util.List;
import java.util.Optional;

/** Where the forced check-ins are kept: one per stay (a stay checks in once). */
public interface ForcedCheckIns {

  Optional<ForcedCheckIn> of(String stayId);

  ForcedCheckIn save(ForcedCheckIn forced);

  /** The ones still incomplete, oldest first. */
  List<ForcedCheckIn> open();
}
