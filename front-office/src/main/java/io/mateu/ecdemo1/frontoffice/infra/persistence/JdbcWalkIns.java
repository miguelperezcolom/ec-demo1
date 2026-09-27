package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** One row per walk-in. Plain SQL — an update, or an insert if there was none — on H2 and PostgreSQL alike. */
@Repository
class JdbcWalkIns implements WalkIns {

  static final RowMapper<WalkIn> ROW = (rs, n) -> new WalkIn(
      rs.getString("stay_id"), rs.getString("request"), rs.getBigDecimal("expected_total"),
      WalkIn.WalkInStatus.valueOf(rs.getString("status")), rs.getString("locator"), rs.getString("pms_reservation_id"),
      rs.getString("message"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("booked_at")));

  final JdbcTemplate jdbc;

  JdbcWalkIns(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<WalkIn> of(String stayId) {
    return jdbc.query("select * from walk_in where stay_id = ?", ROW, stayId).stream().findFirst();
  }

  @Override
  public Optional<WalkIn> byLocator(String locator) {
    return jdbc.query("select * from walk_in where locator = ?", ROW, locator).stream().findFirst();
  }

  @Override
  public void save(WalkIn w) {
    var updated = jdbc.update(
        "update walk_in set request = ?, expected_total = ?, status = ?, locator = ?, pms_reservation_id = ?, message = ?, "
            + "created_at = ?, booked_at = ? where stay_id = ?",
        w.request(), w.expectedTotal(), w.status().name(), w.locator(), w.pmsReservationId(), w.message(),
        timestamp(w.createdAt()), timestamp(w.bookedAt()), w.stayId());
    if (updated == 0) {
      jdbc.update(
          "insert into walk_in (stay_id, request, expected_total, status, locator, pms_reservation_id, message, created_at, booked_at) "
              + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
          w.stayId(), w.request(), w.expectedTotal(), w.status().name(), w.locator(), w.pmsReservationId(), w.message(),
          timestamp(w.createdAt()), timestamp(w.bookedAt()));
    }
  }

  @Override
  public List<WalkIn> pending() {
    return jdbc.query("select * from walk_in where status = 'PENDING' order by created_at", ROW);
  }

  static Instant instant(Timestamp t) {
    return t == null ? null : t.toInstant();
  }

  static Timestamp timestamp(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }
}
