package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Each pax's recognition ({@code pax_recognition}, candidates as JSON) and last scan ({@code pax_scan}). Plain SQL. */
@Repository
class JdbcPaxRecognitions implements PaxRecognitions {

  static final JsonMapper JSON = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  static final TypeReference<List<Named>> NAMED = new TypeReference<>() {};

  final JdbcTemplate jdbc;

  JdbcPaxRecognitions(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<PaxRecognition> of(String stayId, int pax) {
    return jdbc.query("select * from pax_recognition where stay_id = ? and pax = ?", (rs, n) -> recognition(rs),
        stayId, pax).stream().findFirst();
  }

  PaxRecognition recognition(ResultSet rs) throws SQLException {
    var candidates = rs.getString("candidates");
    var at = rs.getTimestamp("confirmed_at");
    return new PaxRecognition(rs.getString("stay_id"), rs.getInt("pax"), rs.getString("customer_id"),
        rs.getString("customer_name"), Certainty.valueOf(rs.getString("certainty")),
        MatchedBy.valueOf(rs.getString("matched_by")),
        candidates == null || candidates.isBlank() ? List.of() : JSON.readValue(candidates, NAMED),
        at == null ? null : at.toInstant(), rs.getString("confirmed_by"), rs.getString("riu_class"));
  }

  @Override
  public void save(PaxRecognition r) {
    clear(r.stayId(), r.pax());
    jdbc.update("insert into pax_recognition (stay_id, pax, customer_id, customer_name, certainty, matched_by, candidates, "
            + "riu_class, confirmed_at, confirmed_by, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        r.stayId(), r.pax(), r.customerId(), r.customerName(), r.certainty().name(), r.matchedBy().name(),
        r.candidates().isEmpty() ? null : JSON.writeValueAsString(r.candidates()), r.riuClass(),
        r.confirmedAt() == null ? null : Timestamp.from(r.confirmedAt()), r.confirmedBy(), Timestamp.from(Instant.now()));
  }

  @Override
  public void clear(String stayId, int pax) {
    jdbc.update("delete from pax_recognition where stay_id = ? and pax = ?", stayId, pax);
  }

  @Override
  public Optional<PaxScan> scanOf(String stayId, int pax) {
    return jdbc.query("select * from pax_scan where stay_id = ? and pax = ?", (rs, n) -> new PaxScan(
        rs.getString("stay_id"), rs.getInt("pax"), rs.getString("first_name"), rs.getString("last_name"),
        rs.getString("document_type"), rs.getString("document_number"), date(rs.getDate("birth_date")),
        rs.getString("nationality"), rs.getString("issuing_country"), date(rs.getDate("expiry")), rs.getString("lookup"),
        rs.getString("found_customer_id")), stayId, pax).stream().findFirst();
  }

  @Override
  public void saveScan(PaxScan s) {
    jdbc.update("delete from pax_scan where stay_id = ? and pax = ?", s.stayId(), s.pax());
    jdbc.update("insert into pax_scan (stay_id, pax, first_name, last_name, document_type, document_number, birth_date, "
            + "nationality, issuing_country, expiry, lookup, found_customer_id, scanned_at) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        s.stayId(), s.pax(), s.firstName(), s.lastName(), s.documentType(), s.documentNumber(), sql(s.birthDate()),
        s.nationality(), s.issuingCountry(), sql(s.expiry()), s.lookup(), s.foundCustomerId(), Timestamp.from(Instant.now()));
  }

  static LocalDate date(Date date) {
    return date == null ? null : date.toLocalDate();
  }

  static Date sql(LocalDate date) {
    return date == null ? null : Date.valueOf(date);
  }
}
