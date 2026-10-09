package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.guest.PaxKardexes;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The desk's kárdex of each pax ({@code pax_kardex}). Plain SQL. */
@Repository
class JdbcPaxKardexes implements PaxKardexes {

  final JdbcTemplate jdbc;

  JdbcPaxKardexes(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<PaxKardex> of(String stayId, int pax) {
    return jdbc.query("select * from pax_kardex where stay_id = ? and pax = ?", (rs, n) -> {
      var at = rs.getTimestamp("filled_at");
      var consent = rs.getObject("marketing_consent") instanceof Boolean b ? b : null;
      return new PaxKardex(rs.getString("stay_id"), rs.getInt("pax"), rs.getString("first_name"),
          rs.getString("last_name"), rs.getString("riu_class"), JdbcPaxRecognitions.date(rs.getDate("document_issue_date")),
          rs.getString("language"), rs.getString("province"), rs.getString("fax"), consent,
          at == null ? null : at.toInstant(), rs.getString("filled_by"));
    }, stayId, pax).stream().findFirst();
  }

  @Override
  public void save(PaxKardex k) {
    jdbc.update("delete from pax_kardex where stay_id = ? and pax = ?", k.stayId(), k.pax());
    jdbc.update("insert into pax_kardex (stay_id, pax, first_name, last_name, riu_class, document_issue_date, language, "
            + "province, fax, marketing_consent, filled_at, filled_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        k.stayId(), k.pax(), k.firstName(), k.lastName(), k.riuClass(), JdbcPaxRecognitions.sql(k.documentIssueDate()),
        k.language(), k.province(), k.fax(), k.marketingConsent(),
        k.filledAt() == null ? null : Timestamp.from(k.filledAt()), k.filledBy());
  }
}
