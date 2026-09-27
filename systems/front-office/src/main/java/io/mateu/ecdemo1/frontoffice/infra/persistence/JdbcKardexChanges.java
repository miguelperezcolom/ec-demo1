package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChanges;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** One row per guest: its last change. Plain SQL — an update, or an insert if there was none — on H2 and PostgreSQL alike. */
@Repository
class JdbcKardexChanges implements KardexChanges {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final TypeReference<List<KardexChange.FieldChange>> FIELDS = new TypeReference<>() {};

  static final RowMapper<KardexChange> ROW = (rs, n) -> new KardexChange(
      rs.getString("guest_id"), rs.getString("request_id"),
      KardexChange.KardexStatus.valueOf(rs.getString("status")), rs.getString("changes"),
      fields(rs.getString("fields")), rs.getString("reason"),
      instant(rs.getTimestamp("requested_at")), instant(rs.getTimestamp("decided_at")), rs.getBoolean("synced"));

  final JdbcTemplate jdbc;

  JdbcKardexChanges(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<KardexChange> of(String guestId) {
    return jdbc.query("select * from guest_kardex where guest_id = ?", ROW, guestId).stream().findFirst();
  }

  @Override
  public void save(KardexChange c) {
    var updated = jdbc.update(
        "update guest_kardex set request_id = ?, status = ?, changes = ?, fields = ?, reason = ?, requested_at = ?, decided_at = ?, synced = ? where guest_id = ?",
        c.requestId(), c.status().name(), c.changes(), json(c.fields()), c.reason(), timestamp(c.requestedAt()),
        timestamp(c.decidedAt()), c.synced(), c.guestId());
    if (updated == 0) {
      jdbc.update(
          "insert into guest_kardex (guest_id, request_id, status, changes, fields, reason, requested_at, decided_at, synced) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
          c.guestId(), c.requestId(), c.status().name(), c.changes(), json(c.fields()), c.reason(), timestamp(c.requestedAt()),
          timestamp(c.decidedAt()), c.synced());
    }
  }

  @Override
  public List<KardexChange> unsynced() {
    return jdbc.query("select * from guest_kardex where synced = false order by requested_at", ROW);
  }

  /** The fields as JSON: a row written before the kardex kept them has none. */
  static List<KardexChange.FieldChange> fields(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return JSON.readValue(json, FIELDS);
    } catch (Exception e) {
      return List.of();
    }
  }

  static String json(List<KardexChange.FieldChange> fields) {
    try {
      return JSON.writeValueAsString(fields);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static Instant instant(Timestamp t) {
    return t == null ? null : t.toInstant();
  }

  static Timestamp timestamp(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }
}
