package io.mateu.ecdemo1.frontoffice.domain.guest;

import java.util.Collection;
import java.util.List;

/** Where the front office keeps the chain's customers' reception notices, as the MDM sends them. */
public interface CustomerNotices {

  /** Every notice of these customers, active or not. */
  List<CustomerNotice> of(Collection<String> customerIds);

  /** Keeps the notice unless the one kept is newer or the same; whether it was kept. */
  boolean save(CustomerNotice notice);
}
