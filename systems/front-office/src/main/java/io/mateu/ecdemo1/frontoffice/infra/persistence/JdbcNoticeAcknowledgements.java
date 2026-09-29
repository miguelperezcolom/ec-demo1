package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.stay.NoticeAcknowledgements;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** One row per stay and moment: the last acknowledgement. Plain SQL, on H2 and PostgreSQL alike. */
@Repository
class JdbcNoticeAcknowledgements implements NoticeAcknowledgements {

  final JdbcTemplate jdbc;

  JdbcNoticeAcknowledgements(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Acknowledgement> of(String stayId, Moment moment) {
    return jdbc.query("select * from stay_notice_ack where stay_id = ? and moment = ?",
        (rs, n) -> new Acknowledgement(rs.getString("stay_id"), Moment.valueOf(rs.getString("moment")),
            rs.getString("fingerprint"), rs.getString("acknowledged_by"),
            rs.getTimestamp("acknowledged_at") == null ? null : rs.getTimestamp("acknowledged_at").toInstant()),
        stayId, moment.name()).stream().findFirst();
  }

  @Override
  public void save(Acknowledgement a) {
    var at = a.at() == null ? null : Timestamp.from(a.at());
    var updated = jdbc.update("update stay_notice_ack set fingerprint = ?, acknowledged_by = ?, acknowledged_at = ? "
        + "where stay_id = ? and moment = ?", a.fingerprint(), a.by(), at, a.stayId(), a.moment().name());
    if (updated == 0) {
      jdbc.update("insert into stay_notice_ack (stay_id, moment, fingerprint, acknowledged_by, acknowledged_at) "
          + "values (?, ?, ?, ?, ?)", a.stayId(), a.moment().name(), a.fingerprint(), a.by(), at);
    }
  }
}
