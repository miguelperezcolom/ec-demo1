package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.registration.PaxRegistrationData;
import io.mateu.ecdemo1.frontoffice.domain.registration.RegistrationRuleCopies;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The registration rules this front office keeps ({@code registration_rule}, the whole rule as JSON,
 * its last version) and each pax's registration data ({@code pax_registration_data}, a row per field).
 * Plain SQL, on H2 and PostgreSQL alike.
 */
@Repository
class JdbcRegistration implements RegistrationRuleCopies, PaxRegistrationData {

  static final JsonMapper JSON = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  final JdbcTemplate jdbc;

  JdbcRegistration(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean save(RegistrationRuleChanged r) {
    var payload = JSON.writeValueAsString(r);
    var updated = jdbc.update("update registration_rule set version = ?, scope_key = ?, active = ?, payload = ?, "
            + "updated_at = ? where rule_id = ? and version < ?",
        r.version(), r.key(), r.active(), payload, now(r.occurredAt()), r.ruleId(), r.version());
    if (updated > 0) {
      return true;
    }
    var known = jdbc.queryForObject("select count(*) from registration_rule where rule_id = ?", Integer.class, r.ruleId());
    if (known != null && known > 0) {
      return false; // the one kept is this version or a newer one
    }
    jdbc.update("insert into registration_rule (rule_id, version, scope_key, active, payload, updated_at) "
        + "values (?, ?, ?, ?, ?, ?)", r.ruleId(), r.version(), r.key(), r.active(), payload, now(r.occurredAt()));
    return true;
  }

  @Override
  public List<RegistrationRuleChanged> all() {
    return jdbc.query("select payload from registration_rule order by rule_id",
        (rs, n) -> JSON.readValue(rs.getString("payload"), RegistrationRuleChanged.class));
  }

  @Override
  public Map<Field, String> of(String stayId, int pax) {
    var values = new EnumMap<Field, String>(Field.class);
    jdbc.query("select field, field_value from pax_registration_data where stay_id = ? and pax = ?", rs -> {
      try {
        values.put(Field.valueOf(rs.getString("field")), rs.getString("field_value"));
      } catch (IllegalArgumentException e) {
        // a field the rules no longer name: left out
      }
    }, stayId, pax);
    return values;
  }

  @Override
  public void put(String stayId, int pax, Map<Field, String> values) {
    var at = Timestamp.from(Instant.now());
    values.forEach((field, value) -> {
      jdbc.update("delete from pax_registration_data where stay_id = ? and pax = ? and field = ?", stayId, pax, field.name());
      if (value != null && !value.isBlank()) {
        jdbc.update("insert into pax_registration_data (stay_id, pax, field, field_value, updated_at) values (?, ?, ?, ?, ?)",
            stayId, pax, field.name(), value.trim(), at);
      }
    });
  }

  static Timestamp now(Instant at) {
    return Timestamp.from(at == null ? Instant.now() : at);
  }
}
