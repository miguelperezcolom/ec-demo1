package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.stay.ForcedCheckIn;
import io.mateu.ecdemo1.frontoffice.domain.stay.ForcedCheckIns;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** One row per forced check-in. Plain SQL, on H2 and PostgreSQL alike. */
@Repository
class JdbcForcedCheckIns implements ForcedCheckIns {

  final JdbcTemplate jdbc;

  JdbcForcedCheckIns(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ForcedCheckIn> of(String stayId) {
    return RequestCache.get("forced-check-in:" + stayId,
        () -> jdbc.query("select * from forced_check_in where stay_id = ?", (rs, n) -> row(rs), stayId).stream()
            .findFirst());
  }

  @Override
  public ForcedCheckIn save(ForcedCheckIn f) {
    RequestCache.evict("forced-check-in:" + f.stayId());
    var updated = jdbc.update("update forced_check_in set forced_by = ?, forced_at = ?, reason = ?, missing = ?, "
            + "completed_by = ?, completed_at = ?, overdue_notified_at = ? where stay_id = ?",
        f.forcedBy(), ts(f.forcedAt()), f.reason(), f.missingWhenForced(), f.completedBy(), ts(f.completedAt()),
        ts(f.overdueNotifiedAt()), f.stayId());
    if (updated == 0) {
      jdbc.update("insert into forced_check_in (stay_id, forced_by, forced_at, reason, missing, completed_by, "
              + "completed_at, overdue_notified_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
          f.stayId(), f.forcedBy(), ts(f.forcedAt()), f.reason(), f.missingWhenForced(), f.completedBy(),
          ts(f.completedAt()), ts(f.overdueNotifiedAt()));
    }
    return f;
  }

  @Override
  public List<ForcedCheckIn> open() {
    return jdbc.query("select * from forced_check_in where completed_at is null order by forced_at",
        (rs, n) -> row(rs));
  }

  static ForcedCheckIn row(ResultSet rs) throws SQLException {
    return new ForcedCheckIn(rs.getString("stay_id"), rs.getString("forced_by"), instant(rs, "forced_at"),
        rs.getString("reason"), rs.getString("missing"), rs.getString("completed_by"), instant(rs, "completed_at"),
        instant(rs, "overdue_notified_at"));
  }

  static Instant instant(ResultSet rs, String column) throws SQLException {
    var ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant();
  }

  static Timestamp ts(Instant at) {
    return at == null ? null : Timestamp.from(at);
  }
}
