package io.mateu.ecdemo1.frontoffice.infra.mcp;

import io.mateu.ecdemo1.agentsql.AgentSql;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The front office's data in read-only SQL, for the questions {@code searchStays} and the other
 * tools do not answer: counts, groupings, crossings. Over the views of agent-sql/views.sql, as a
 * login that can read only those (doc/src/content/docs/ia/datos-del-agente.md). Named after the front office so that, beside
 * another service's, the agent tells them apart — and so does the tool-usage metric.
 */
public class FrontOfficeDataTools {

  final AgentSql sql;

  public FrontOfficeDataTools(AgentSql sql) {
    this.sql = sql;
  }

  @Tool(description = "The front office's data views for queryFrontOfficeData — stays, stay_pax, guests, rooms, "
      + "incidents, folio_lines, payments — with their columns and what each means. Read it before writing SQL")
  public List<AgentSql.View> describeFrontOfficeData(
      @ToolParam(description = "One view's name; empty for all", required = false) String view) {
    return sql.describe(view);
  }

  @Tool(description = "Read-only SQL (PostgreSQL, one SELECT) over the front office's data views, for what the "
      + "other tools do not answer: counts, sums, groupings, crossings (e.g. stays per nationality this month, "
      + "occupancy by room type, incidents per type). Only the views describeFrontOfficeData lists, unqualified "
      + "or as agent.<view>. Returns the columns and at most 200 rows: aggregate in SQL rather than reading rows. "
      + "For a list of stays with filters, searchStays is simpler")
  public AgentSql.Result queryFrontOfficeData(@ToolParam(description = "One SELECT") String sql) {
    return this.sql.query(sql);
  }
}
