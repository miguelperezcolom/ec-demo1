package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.customer.ArrivalBriefings;
import io.mateu.ecdemo1.frontoffice.domain.customer.LoyaltyStatus;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory.HistorySummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** The arrivals' briefings ({@code arrival_briefing}, the summary and the standing as JSON). Plain SQL. */
@Repository
class JdbcArrivalBriefings implements ArrivalBriefings {

  static final JsonMapper JSON = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  final JdbcTemplate jdbc;

  JdbcArrivalBriefings(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Briefing> of(String stayId, int pax) {
    return jdbc.query("select * from arrival_briefing where stay_id = ? and pax = ?", (rs, n) -> briefing(rs),
        stayId, pax).stream().findFirst();
  }

  @Override
  public List<Briefing> all() {
    return jdbc.query("select * from arrival_briefing order by stay_id, pax", (rs, n) -> briefing(rs));
  }

  Briefing briefing(ResultSet rs) throws SQLException {
    var loyalty = rs.getString("loyalty");
    return new Briefing(rs.getString("stay_id"), rs.getInt("pax"), rs.getString("customer_id"),
        rs.getString("customer_name"), JSON.readValue(rs.getString("history"), HistorySummary.class),
        loyalty == null || loyalty.isBlank() ? null : JSON.readValue(loyalty, LoyaltyStatus.Loyalty.class),
        rs.getTimestamp("prepared_at").toInstant());
  }

  @Override
  public void save(Briefing b) {
    clear(b.stayId(), b.pax());
    jdbc.update("insert into arrival_briefing (stay_id, pax, customer_id, customer_name, history, loyalty, prepared_at) "
            + "values (?, ?, ?, ?, ?, ?, ?)",
        b.stayId(), b.pax(), b.customerId(), b.customerName(), JSON.writeValueAsString(b.history()),
        b.loyalty() == null ? null : JSON.writeValueAsString(b.loyalty()), Timestamp.from(b.preparedAt()));
  }

  @Override
  public void clear(String stayId, int pax) {
    jdbc.update("delete from arrival_briefing where stay_id = ? and pax = ?", stayId, pax);
  }

  @Override
  public int keepOnly(Collection<String> stayIds) {
    var keep = new HashSet<>(stayIds);
    var gone = jdbc.queryForList("select distinct stay_id from arrival_briefing", String.class).stream()
        .filter(id -> !keep.contains(id)).toList();
    gone.forEach(id -> jdbc.update("delete from arrival_briefing where stay_id = ?", id));
    return gone.size();
  }
}
