package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOps;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * One row per stay the desk worked on. It used to be a map in memory: a restart forgot every
 * operation, and a check-in that failed halfway could not take its flags back with the rest.
 */
@Repository
class JdbcCheckInOps implements CheckInOpsRepository {

  static final RowMapper<CheckInOps> ROW = (rs, n) -> new CheckInOps(rs.getBoolean("wifi"), rs.getBoolean("llave"),
      rs.getBoolean("firma"), rs.getBoolean("cobro"), rs.getBoolean("extras"), pax(rs.getString("no_show_pax")));

  final JdbcTemplate jdbc;

  JdbcCheckInOps(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public CheckInOps of(String stayId) {
    return RequestCache.get("check-in-ops:" + stayId,
            () -> jdbc.query("select * from check_in_ops where stay_id = ?", ROW, stayId).stream().findFirst())
        .orElse(CheckInOps.none());
  }

  @Override
  public CheckInOps save(String stayId, CheckInOps ops) {
    RequestCache.evict("check-in-ops:" + stayId);
    var noShows = text(ops.noShowPax());
    var updated = jdbc.update(
        "update check_in_ops set wifi = ?, llave = ?, firma = ?, cobro = ?, extras = ?, no_show_pax = ? where stay_id = ?",
        ops.wifi(), ops.llave(), ops.firma(), ops.cobro(), ops.extras(), noShows, stayId);
    if (updated == 0) {
      jdbc.update("insert into check_in_ops (stay_id, wifi, llave, firma, cobro, extras, no_show_pax) values (?, ?, ?, ?, ?, ?, ?)",
          stayId, ops.wifi(), ops.llave(), ops.firma(), ops.cobro(), ops.extras(), noShows);
    }
    return ops;
  }

  static Set<Integer> pax(String text) {
    return text == null || text.isBlank() ? Set.of()
        : Arrays.stream(text.split(",")).map(String::trim).map(Integer::valueOf).collect(Collectors.toUnmodifiableSet());
  }

  static String text(Set<Integer> pax) {
    return pax == null || pax.isEmpty() ? null
        : pax.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
  }
}
