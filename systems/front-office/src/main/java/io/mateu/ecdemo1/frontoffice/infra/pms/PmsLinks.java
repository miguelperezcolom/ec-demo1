package io.mateu.ecdemo1.frontoffice.infra.pms;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Which PMS reservation each stay is, and the PMS's version of it last written here — columns of the
 * {@code stay} table the Stay aggregate does not map, so nothing the desk does touches them.
 */
@Repository
public class PmsLinks {

  public record Link(String stayId, String pmsReservationId, String pmsVersion) {}

  final JdbcTemplate jdbc;

  public PmsLinks(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The stay a PMS reservation is, if the front office has it. */
  public Optional<Link> byPmsReservation(String pmsReservationId) {
    return jdbc.query("select id, pms_reservation_id, pms_version from stay where pms_reservation_id = ?",
        (rs, n) -> new Link(rs.getString("id"), rs.getString("pms_reservation_id"), rs.getString("pms_version")),
        pmsReservationId).stream().findFirst();
  }

  public Optional<Link> ofStay(String stayId) {
    return jdbc.query("select id, pms_reservation_id, pms_version from stay where id = ?",
        (rs, n) -> new Link(rs.getString("id"), rs.getString("pms_reservation_id"), rs.getString("pms_version")),
        stayId).stream().findFirst();
  }

  /** The stay is this PMS reservation, as of this version; the rate plan in words, when known. */
  public void link(String stayId, String pmsReservationId, String pmsVersion, String ratePlan) {
    jdbc.update("update stay set pms_reservation_id = ?, pms_version = ?, rate_plan = ? where id = ?",
        pmsReservationId, pmsVersion, ratePlan, stayId);
  }
}
