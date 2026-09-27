package io.mateu.ecdemo1.frontoffice.infra.pms;

import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCatalogueSummary;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueEntry;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The PMS's catalogue of the property, as the pms-fo integration gave it: how the front office reads
 * the PMS's codes — «Estándar King», not STDK; «Desayuno», not BRKFST.
 */
@Repository
public class PmsCatalogue {

  final JdbcTemplate jdbc;

  public PmsCatalogue(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The property's catalogue, whole, in place of the one it had. In the caller's transaction. */
  public void replace(String pmsHotel, String commandId, List<CatalogueEntry> entries, Instant at) {
    jdbc.update("delete from pms_catalogue where pms_hotel = ?", pmsHotel);
    var seen = new java.util.HashSet<String>();
    for (var e : entries == null ? List.<CatalogueEntry>of() : entries) {
      if (e == null || e.type() == null || e.code() == null || !seen.add(e.type() + ":" + e.code())) {
        continue;
      }
      jdbc.update("insert into pms_catalogue (pms_hotel, type, code, description, extra) values (?, ?, ?, ?, ?)",
          pmsHotel, e.type().name(), e.code(), cut(e.description(), 300), cut(e.extra(), 100));
    }
    jdbc.update("delete from pms_catalogue_sync where pms_hotel = ?", pmsHotel);
    jdbc.update("insert into pms_catalogue_sync (pms_hotel, command_id, synced_at) values (?, ?, ?)", pmsHotel,
        commandId, Timestamp.from(at));
  }

  /** What a code is in the PMS's words, if the catalogue says. */
  public Optional<String> describe(String pmsHotel, CatalogueType type, String code) {
    if (code == null) {
      return Optional.empty();
    }
    return jdbc.queryForList("select description from pms_catalogue where pms_hotel = ? and type = ? and code = ?",
            String.class, pmsHotel, type.name(), code).stream()
        .filter(d -> d != null && !d.isBlank()).findFirst();
  }

  public List<CatalogueEntry> list(String pmsHotel, CatalogueType type) {
    return jdbc.query("select type, code, description, extra from pms_catalogue where pms_hotel = ?"
            + (type == null ? "" : " and type = '" + type.name() + "'") + " order by type, code",
        (rs, n) -> new CatalogueEntry(CatalogueType.valueOf(rs.getString("type")), rs.getString("code"),
            rs.getString("description"), rs.getString("extra")), pmsHotel);
  }

  /** What the front office holds of the property's catalogue: empty counts if it was never given one. */
  public FrontOfficeCatalogueSummary summary(String pmsHotel) {
    var sync = jdbc.query("select command_id, synced_at from pms_catalogue_sync where pms_hotel = ?",
        (rs, n) -> new Object[] {rs.getString("command_id"), rs.getTimestamp("synced_at").toInstant()}, pmsHotel);
    if (sync.isEmpty()) {
      return new FrontOfficeCatalogueSummary(null, null, null, new TreeMap<>());
    }
    var counts = new TreeMap<String, Integer>();
    jdbc.query("select type, count(*) as n from pms_catalogue where pms_hotel = ? group by type",
        rs -> {
          counts.put(rs.getString("type"), rs.getInt("n"));
        }, pmsHotel);
    return new FrontOfficeCatalogueSummary(pmsHotel, (String) sync.get(0)[0], (Instant) sync.get(0)[1], counts);
  }

  static String cut(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
