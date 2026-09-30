package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNationalities;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code customer_nationality}: a row per customer, with where its nationality came from. */
@Repository
class JdbcCustomerNationalities implements CustomerNationalities {

  final JdbcTemplate jdbc;

  JdbcCustomerNationalities(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<String> of(String customerId) {
    if (customerId == null) {
      return Optional.empty();
    }
    return jdbc.query("select nationality from customer_nationality where customer_id = ?",
            (rs, n) -> rs.getString(1), customerId).stream()
        .filter(v -> v != null && !v.isBlank()).findFirst();
  }

  @Override
  public void put(String customerId, String nationality, String source) {
    if (customerId == null || customerId.isBlank()) {
      return;
    }
    var value = nationality == null || nationality.isBlank() ? null : nationality.trim().toUpperCase();
    var now = Timestamp.from(Instant.now());
    var updated = jdbc.update(
        "update customer_nationality set nationality = ?, source = ?, updated_at = ? where customer_id = ?",
        value, source, now, customerId);
    if (updated == 0) {
      jdbc.update("insert into customer_nationality (customer_id, nationality, source, updated_at) values (?, ?, ?, ?)",
          customerId, value, source, now);
    }
  }

  @Override
  public List<String> unknown(int limit) {
    return jdbc.queryForList(
        "select id from (select g.id as id from guest g union select c.companion_id as id from stay_companion c) ids"
            + " where id is not null and not exists (select 1 from customer_nationality n where n.customer_id = ids.id)"
            + " fetch first " + Math.max(1, limit) + " rows only",
        String.class);
  }
}
