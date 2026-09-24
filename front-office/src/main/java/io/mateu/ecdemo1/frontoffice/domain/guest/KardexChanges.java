package io.mateu.ecdemo1.frontoffice.domain.guest;

import java.util.List;
import java.util.Optional;

/** Where the cardex keeps each guest's last change to the master's data. */
public interface KardexChanges {

  Optional<KardexChange> of(String guestId);

  void save(KardexChange change);

  List<KardexChange> unsynced();
}
