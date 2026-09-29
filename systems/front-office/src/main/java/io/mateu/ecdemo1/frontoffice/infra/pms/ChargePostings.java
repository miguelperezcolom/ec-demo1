package io.mateu.ecdemo1.frontoffice.infra.pms;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * How the PMS took each charge of the desk (pms-fo, «registrar-cargo», «anular-cargo»): its posting on
 * the PMS's folio — the transaction number —, its reversal's, or the PMS's refusal and why. Written by
 * the PMS's answers (RecordCharge), by state: told twice, the same. A table of its own, so that the
 * PMS's answer never races the desk's writes to the folio.
 */
@Repository
public class ChargePostings {

  /** Where a line of the folio stands in the PMS. */
  public record Posting(String lineId, String stayId, String postingId, String reversalId, String state, Instant at) {

    /** Whether the PMS refused the charge (or its void) — the state says why. */
    public boolean refused() {
      return state != null && state.startsWith("Opera: rechazado");
    }
  }

  final JdbcTemplate jdbc;

  public ChargePostings(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The PMS posted the line (its charge, or its reversal); {@code postingId} its transaction number. */
  public void posted(String stayId, String lineId, boolean reversal, String postingId, String state, Instant at) {
    var column = reversal ? "reversal_id" : "posting_id";
    var updated = jdbc.update("update folio_line_pms set " + column + " = coalesce(?, " + column + "), state = ?, "
        + "updated_at = ? where line_id = ?", postingId, state, Timestamp.from(at), lineId);
    if (updated == 0) {
      jdbc.update("insert into folio_line_pms (line_id, stay_id, " + column + ", state, updated_at) values (?, ?, ?, ?, ?)",
          lineId, stayId, postingId, state, Timestamp.from(at));
    }
  }

  /** What the desk reads before the PMS answers: the charge (or its void) is on its way. */
  public void pending(String stayId, String lineId, String state, Instant at) {
    var updated = jdbc.update("update folio_line_pms set state = ?, updated_at = ? where line_id = ?", state,
        Timestamp.from(at), lineId);
    if (updated == 0) {
      jdbc.update("insert into folio_line_pms (line_id, stay_id, state, updated_at) values (?, ?, ?, ?)", lineId, stayId,
          state, Timestamp.from(at));
    }
  }

  public Optional<Posting> of(String lineId) {
    return jdbc.query("select line_id, stay_id, posting_id, reversal_id, state, updated_at from folio_line_pms "
        + "where line_id = ?", (rs, n) -> row(rs), lineId).stream().findFirst();
  }

  /** The stay's lines, by line id. */
  public Map<String, Posting> ofStay(String stayId) {
    List<Posting> rows = jdbc.query("select line_id, stay_id, posting_id, reversal_id, state, updated_at from "
        + "folio_line_pms where stay_id = ?", (rs, n) -> row(rs), stayId);
    return rows.stream().collect(Collectors.toMap(Posting::lineId, p -> p));
  }

  static Posting row(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new Posting(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
        rs.getTimestamp(6).toInstant());
  }
}
