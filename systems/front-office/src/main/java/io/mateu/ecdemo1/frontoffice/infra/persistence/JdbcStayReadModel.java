package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.stay.StayReadModel;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The stays' read side in plain SQL, on H2 and PostgreSQL alike. */
@Repository
class JdbcStayReadModel implements StayReadModel {

  final JdbcTemplate jdbc;

  JdbcStayReadModel(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<StayRow> rows() {
    return jdbc.query(
        "select s.id, s.status, s.check_in, s.check_out, s.room_number, s.room_type, g.name, g.tier"
            + " from stay s join guest g on g.id = s.guest_id",
        (rs, n) -> new StayRow(
            rs.getString("id"),
            StayStatus.valueOf(rs.getString("status")),
            rs.getDate("check_in").toLocalDate(),
            rs.getDate("check_out").toLocalDate(),
            rs.getString("room_number"),
            rs.getString("room_type"),
            rs.getString("name"),
            rs.getString("tier")));
  }

  @Override
  public Today today(LocalDate today) {
    var day = Date.valueOf(today);
    return jdbc.queryForObject(
        "select"
            + " coalesce(sum(case when status = 'ARRIVING' and check_in <= ? then 1 else 0 end), 0) as arrivals,"
            + " coalesce(sum(case when status = 'IN_HOUSE' then 1 else 0 end), 0) as in_house,"
            + " coalesce(sum(case when status in ('IN_HOUSE', 'DEPARTED') and check_out = ? then 1 else 0 end), 0) as departures"
            + " from stay",
        (rs, n) -> new Today(rs.getLong("arrivals"), rs.getLong("in_house"), rs.getLong("departures")),
        day, day);
  }

  @Override
  public List<Long> occupiedNights(LocalDate from, int days) {
    // Only the stays that occupy a room and overlap the window, and only their dates; counted per
    // night here rather than with generate_series, which H2 does not have.
    record Span(LocalDate checkIn, LocalDate checkOut) {}
    var last = from.plusDays(days - 1L);
    var spans = jdbc.query(
        "select check_in, check_out from stay"
            + " where status in ('ARRIVING', 'IN_HOUSE') and check_in <= ? and check_out > ?",
        (rs, n) -> new Span(rs.getDate("check_in").toLocalDate(), rs.getDate("check_out").toLocalDate()),
        Date.valueOf(last), Date.valueOf(from));
    var nights = new ArrayList<Long>(days);
    for (int i = 0; i < days; i++) {
      var night = from.plusDays(i);
      nights.add(spans.stream()
          .filter(s -> !s.checkIn().isAfter(night) && s.checkOut().isAfter(night))
          .count());
    }
    return nights;
  }

  @Override
  public long rooms() {
    var count = jdbc.queryForObject("select count(*) from room", Long.class);
    return count == null ? 0 : count;
  }
}
