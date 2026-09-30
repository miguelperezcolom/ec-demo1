package io.mateu.ecdemo1.frontoffice.domain.notice;

import java.util.Collection;
import java.util.List;

/** Where the front office keeps the reception notices, as the notices service sends them. */
public interface Notices {

  /** Every notice of these subjects of that kind, active or not. */
  List<Notice> of(Notice.Subject subject, Collection<String> subjectIds);

  /** Every active notice of that kind of subject — the partners', which a stay knows by name. */
  List<Notice> active(Notice.Subject subject);

  /** Keeps the notice unless the one kept is newer or the same; whether it was kept. */
  boolean save(Notice notice);
}
