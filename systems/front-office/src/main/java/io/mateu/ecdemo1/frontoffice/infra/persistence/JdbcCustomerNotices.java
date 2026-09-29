package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNotice;
import io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNotices;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** One row per notice, the MDM's last version of it. Plain SQL, on H2 and PostgreSQL alike. */
@Repository
class JdbcCustomerNotices implements CustomerNotices {

  static final RowMapper<CustomerNotice> ROW = (rs, n) -> new CustomerNotice(
      rs.getString("notice_id"), rs.getString("customer_id"), rs.getLong("version"), rs.getString("text"),
      rs.getString("type") == null ? CustomerNotice.Type.INFORMATIVE : CustomerNotice.Type.valueOf(rs.getString("type")),
      rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toLocalDate(),
      rs.getDate("valid_to") == null ? null : rs.getDate("valid_to").toLocalDate(),
      moments(rs.getString("show_at")), rs.getBoolean("active"),
      rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant());

  final JdbcTemplate jdbc;

  JdbcCustomerNotices(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<CustomerNotice> of(Collection<String> customerIds) {
    var ids = customerIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
    if (ids.isEmpty()) {
      return List.of();
    }
    var in = String.join(",", ids.stream().map(id -> "?").toList());
    return jdbc.query("select * from customer_notice where customer_id in (" + in + ") order by notice_id", ROW,
        ids.toArray());
  }

  @Override
  public boolean save(CustomerNotice n) {
    var updated = jdbc.update(
        "update customer_notice set customer_id = ?, version = ?, text = ?, type = ?, valid_from = ?, valid_to = ?, "
            + "show_at = ?, active = ?, updated_at = ? where notice_id = ? and version < ?",
        n.customerId(), n.version(), n.text(), n.type().name(), date(n.from()), date(n.to()), moments(n), n.active(),
        timestamp(n), n.noticeId(), n.version());
    if (updated > 0) {
      return true;
    }
    var known = jdbc.queryForObject("select count(*) from customer_notice where notice_id = ?", Integer.class, n.noticeId());
    if (known != null && known > 0) {
      return false; // the one kept is this version or a newer one
    }
    jdbc.update("insert into customer_notice (notice_id, customer_id, version, text, type, valid_from, valid_to, show_at, "
            + "active, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        n.noticeId(), n.customerId(), n.version(), n.text(), n.type().name(), date(n.from()), date(n.to()), moments(n),
        n.active(), timestamp(n));
    return true;
  }

  static String moments(CustomerNotice n) {
    return n.showAt().stream().sorted().map(Enum::name).collect(Collectors.joining(","));
  }

  static java.util.Set<CustomerNotice.Moment> moments(String value) {
    var set = EnumSet.noneOf(CustomerNotice.Moment.class);
    if (value != null && !value.isBlank()) {
      Arrays.stream(value.split(",")).map(String::trim).map(CustomerNotice.Moment::valueOf).forEach(set::add);
    }
    return set;
  }

  static Date date(java.time.LocalDate d) {
    return d == null ? null : Date.valueOf(d);
  }

  static Timestamp timestamp(CustomerNotice n) {
    return n.updatedAt() == null ? null : Timestamp.from(n.updatedAt());
  }
}
