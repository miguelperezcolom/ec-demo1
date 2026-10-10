package io.mateu.ecdemo1.booking.infra.in.mcp;

import io.mateu.ecdemo1.agentsql.AgentSql;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * The CRS's bookings in read-only SQL, for the questions searchBookings and getBooking do not answer:
 * counts, sums, groupings, crossings. Over the views of agent-sql/views-postgresql.sql, as a login that
 * can read only those (doc/src/content/docs/ia/datos-del-agente.md). Named after the CRS so that, beside another service's, the
 * agent tells them apart — and so does the tool-usage metric.
 */
public class CrsDataTools {

    final AgentSql sql;

    public CrsDataTools(AgentSql sql) {
        this.sql = sql;
    }

    @Tool(description = "The CRS's data views for queryCrsData — bookings, booking_rooms, booking_guests, "
            + "booking_payments — with their columns and what each means. Read it before writing SQL")
    public List<AgentSql.View> describeCrsData(
            @ToolParam(description = "One view's name; empty for all", required = false) String view) {
        return sql.describe(view);
    }

    @Tool(description = "Read-only SQL (PostgreSQL, one SELECT) over the CRS's booking views, for what the other "
            + "tools do not answer: counts, sums, groupings, crossings (e.g. bookings and revenue per channel this "
            + "month, room nights per room type, nationalities of next week's arrivals). Only the views "
            + "describeCrsData lists, unqualified or as agent.<view>. Returns the columns and at most 200 rows: "
            + "aggregate in SQL rather than reading rows. For a list of bookings with filters, searchBookings is simpler")
    public AgentSql.Result queryCrsData(@ToolParam(description = "One SELECT") String sql) {
        return this.sql.query(sql);
    }
}
