package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.stay.StayReadModel;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The stays' read side in plain SQL, on H2 and PostgreSQL alike. */
@Repository
class JdbcStayReadModel implements StayReadModel {

  final JdbcTemplate jdbc;

  JdbcStayReadModel(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** A stay with its guest and the holder's nationality: what the desk scanned for pax 1, else the customer's. */
  static final String ROWS =
      "select s.id, s.status, s.check_in, s.check_out, s.room_number, s.room_type, g.name, g.tier,"
          + " coalesce(r.field_value, n.nationality) as nationality"
          + " from stay s join guest g on g.id = s.guest_id"
          + " left join pax_registration_data r on r.stay_id = s.id and r.pax = 1 and r.field = 'NATIONALITY'"
          + " left join customer_nationality n on n.customer_id = g.id";

  @Override
  public List<StayRow> rows() {
    return jdbc.query(ROWS, (rs, n) -> row(rs));
  }

  @Override
  public List<StayRow> search(StaySearch search) {
    var where = new ArrayList<String>();
    var args = new ArrayList<Object>();
    if (search.statuses() != null && !search.statuses().isEmpty()) {
      where.add("s.status in (" + String.join(", ", search.statuses().stream().map(st -> "?").toList()) + ")");
      search.statuses().forEach(st -> args.add(st.name()));
    }
    date(where, args, "s.check_in >= ?", search.arrivalFrom());
    date(where, args, "s.check_in <= ?", search.arrivalTo());
    date(where, args, "s.check_out >= ?", search.departureFrom());
    date(where, args, "s.check_out <= ?", search.departureTo());
    if (search.occupyingOn() != null) {
      where.add("s.check_in <= ? and s.check_out > ?");
      args.add(Date.valueOf(search.occupyingOn()));
      args.add(Date.valueOf(search.occupyingOn()));
    }
    like(where, args, "lower(s.room_type) like ?", search.roomType());
    like(where, args, "lower(s.board) like ?", search.board());
    like(where, args, "lower(s.agency) like ?", search.agency());
    if (search.text() != null && !search.text().isBlank()) {
      where.add("(lower(s.id) like ? or lower(g.name) like ? or lower(s.room_number) like ?)");
      var text = pattern(search.text());
      args.add(text);
      args.add(text);
      args.add(text);
    }
    if (search.nationality() != null && !search.nationality().isBlank()) {
      var iso = search.nationality().trim().toUpperCase(Locale.ROOT);
      var holder = "upper(coalesce(r.field_value, n.nationality)) = ?";
      if (search.holderOnly()) {
        where.add(holder);
        args.add(iso);
      } else {
        // Any pax: the holder, a pax whose registration says it, or a companion the chain knows.
        where.add("(" + holder
            + " or exists (select 1 from pax_registration_data p where p.stay_id = s.id"
            + " and p.field = 'NATIONALITY' and upper(p.field_value) = ?)"
            + " or exists (select 1 from stay_companion c join customer_nationality cn"
            + " on cn.customer_id = c.companion_id where c.stay_id = s.id and upper(cn.nationality) = ?))");
        args.add(iso);
        args.add(iso);
        args.add(iso);
      }
    }
    var sql = ROWS + (where.isEmpty() ? "" : " where " + String.join(" and ", where))
        + " order by s.check_in, s.id limit " + Math.max(1, search.limit());
    return jdbc.query(sql, (rs, n) -> row(rs), args.toArray());
  }

  @Override
  public Map<String, String> holderNationalities(Collection<String> stayIds) {
    var found = new HashMap<String, String>();
    if (stayIds.isEmpty()) {
      return found;
    }
    jdbc.query(ROWS + " where s.id in (" + String.join(", ", stayIds.stream().map(id -> "?").toList()) + ")",
        rs -> {
          if (rs.getString("nationality") != null) {
            found.put(rs.getString("id"), rs.getString("nationality"));
          }
        }, stayIds.toArray());
    return found;
  }

  static void date(List<String> where, List<Object> args, String condition, LocalDate value) {
    if (value != null) {
      where.add(condition);
      args.add(Date.valueOf(value));
    }
  }

  static void like(List<String> where, List<Object> args, String condition, String value) {
    if (value != null && !value.isBlank()) {
      where.add(condition);
      args.add(pattern(value));
    }
  }

  static String pattern(String text) {
    return "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
  }

  static StayRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new StayRow(
        rs.getString("id"),
            StayStatus.valueOf(rs.getString("status")),
        rs.getDate("check_in").toLocalDate(),
        rs.getDate("check_out").toLocalDate(),
        rs.getString("room_number"),
        rs.getString("room_type"),
        rs.getString("name"),
        rs.getString("tier"),
        rs.getString("nationality"));
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
