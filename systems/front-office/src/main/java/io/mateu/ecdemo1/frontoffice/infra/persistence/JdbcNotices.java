package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.notice.Notice;
import io.mateu.ecdemo1.frontoffice.domain.notice.Notices;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * One row per notice, the notices service's last version of it, in the table the customers' notices
 * already lived in ({@code customer_notice}: its rows are kept, and read as the customers' they are).
 * {@code customer_id} is the subject's id, whatever the subject. Plain SQL, on H2 and PostgreSQL alike.
 */
@Repository
class JdbcNotices implements Notices {

  static final RowMapper<Notice> ROW = (rs, n) -> new Notice(
      rs.getString("notice_id"),
      rs.getString("subject_type") == null ? Notice.Subject.CUSTOMER : Notice.Subject.valueOf(rs.getString("subject_type")),
      rs.getString("customer_id"), rs.getString("subject_name"), rs.getString("hotel_code"), rs.getLong("version"),
      rs.getString("text"),
      rs.getString("type") == null ? Notice.Type.INFORMATIVE : Notice.Type.valueOf(rs.getString("type")),
      rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toLocalDate(),
      rs.getDate("valid_to") == null ? null : rs.getDate("valid_to").toLocalDate(),
      moments(rs.getString("show_at")), rs.getBoolean("active"),
      rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant());

  final JdbcTemplate jdbc;

  JdbcNotices(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Notice> of(Notice.Subject subject, Collection<String> subjectIds) {
    var ids = subjectIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
    if (ids.isEmpty()) {
      return List.of();
    }
    var in = String.join(",", ids.stream().map(id -> "?").toList());
    var args = new java.util.ArrayList<Object>();
    args.add(subject.name());
    args.addAll(ids);
    return jdbc.query("select * from customer_notice where subject_type = ? and customer_id in (" + in
        + ") order by notice_id", ROW, args.toArray());
  }

  @Override
  public List<Notice> active(Notice.Subject subject) {
    return jdbc.query("select * from customer_notice where subject_type = ? and active = true order by notice_id", ROW,
        subject.name());
  }

  @Override
  public boolean save(Notice n) {
    var updated = jdbc.update(
        "update customer_notice set subject_type = ?, customer_id = ?, subject_name = ?, hotel_code = ?, version = ?, "
            + "text = ?, type = ?, valid_from = ?, valid_to = ?, show_at = ?, active = ?, updated_at = ? "
            + "where notice_id = ? and version < ?",
        n.subject().name(), n.subjectId(), n.subjectName(), n.hotelCode(), n.version(), n.text(), n.type().name(),
        date(n.from()), date(n.to()), moments(n), n.active(), timestamp(n), n.noticeId(), n.version());
    if (updated > 0) {
      return true;
    }
    var known = jdbc.queryForObject("select count(*) from customer_notice where notice_id = ?", Integer.class, n.noticeId());
    if (known != null && known > 0) {
      return false; // the one kept is this version or a newer one
    }
    jdbc.update("insert into customer_notice (notice_id, subject_type, customer_id, subject_name, hotel_code, version, "
            + "text, type, valid_from, valid_to, show_at, active, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        n.noticeId(), n.subject().name(), n.subjectId(), n.subjectName(), n.hotelCode(), n.version(), n.text(),
        n.type().name(), date(n.from()), date(n.to()), moments(n), n.active(), timestamp(n));
    return true;
  }

  static String moments(Notice n) {
    return n.moments().stream().sorted().map(Enum::name).collect(Collectors.joining(","));
  }

  /** The moments a row names; {@code STAY}, the customers' notices' old name for the stay, is IN_HOUSE. */
  static java.util.Set<Notice.Moment> moments(String value) {
    var set = EnumSet.noneOf(Notice.Moment.class);
    if (value != null && !value.isBlank()) {
      Arrays.stream(value.split(",")).map(String::trim).map(Notice::moment).filter(Objects::nonNull).forEach(set::add);
    }
    return set;
  }

  static Date date(java.time.LocalDate d) {
    return d == null ? null : Date.valueOf(d);
  }

  static Timestamp timestamp(Notice n) {
    return n.updatedAt() == null ? null : Timestamp.from(n.updatedAt());
  }
}
