package io.mateu.ecdemo1.frontoffice.domain.stay;

import java.util.List;
import java.util.Optional;

/** Where the front office keeps its walk-ins and the CRS booking each became. */
public interface WalkIns {

  Optional<WalkIn> of(String stayId);

  /** The walk-in the CRS booked as {@code locator}. */
  Optional<WalkIn> byLocator(String locator);

  void save(WalkIn walkIn);

  /** Those the CRS has not answered yet, oldest first. */
  List<WalkIn> pending();
}
