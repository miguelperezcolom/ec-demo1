package io.mateu.ecdemo1.agentsql;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;

/**
 * Read-only SQL over the views a service publishes for its agents in one schema ({@code agent}):
 * {@link #describe} says what they are — their columns and what each means, from the database's own
 * comments — and {@link #query} answers one SELECT over them.
 *
 * <p>Three walls, the first the one that matters: the query runs as the reader login, which can read
 * the views of that schema and nothing else (no table, no file, no setting, no other database); in a
 * read-only transaction with a timeout, at most {@code maxRows} rows; and only once {@link SqlGuard}
 * has seen it is a single SELECT over those views. A view runs with its owner's rights, so it is the
 * view that decides which columns and rows an agent ever sees.
 */
public class AgentSql {

  /** A view as an agent sees it: its name, what it is and its columns. */
  public record View(String name, String description, List<Column> columns) {}

  public record Column(String name, String type, String description) {}

  /** What a query found: the columns, at most the limit of rows, and whether there were more. */
  public record Result(List<String> columns, List<List<Object>> rows, int rowCount, boolean truncated,
                       String note) {}

  static final int MAX_CELL = 500;

  final DataSource reader;
  final String schema;
  final Duration timeout;
  final int maxRows;
  final SqlGuard guard;

  public AgentSql(DataSource reader, String schema, Duration timeout, int maxRows) {
    this.reader = reader;
    this.schema = schema;
    this.timeout = timeout;
    this.maxRows = maxRows;
    this.guard = new SqlGuard(schema);
  }

  /** Every view, or only the one named, with its columns; descriptions are the views' and columns' comments. */
  public List<View> describe(String only) {
    try (var connection = reader.getConnection()) {
      var meta = connection.getMetaData();
      var schemaName = stored(connection, schema);
      var views = new ArrayList<View>();
      try (var tables = meta.getTables(null, schemaName, "%", new String[] {"VIEW"})) {
        while (tables.next()) {
          var name = tables.getString("TABLE_NAME");
          if (only != null && !only.isBlank() && !name.equalsIgnoreCase(only.trim())) {
            continue;
          }
          var columns = new ArrayList<Column>();
          try (var cols = meta.getColumns(null, schemaName, name, "%")) {
            while (cols.next()) {
              columns.add(new Column(cols.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),
                  cols.getString("TYPE_NAME").toLowerCase(Locale.ROOT), cols.getString("REMARKS")));
            }
          }
          views.add(new View(name.toLowerCase(Locale.ROOT), tables.getString("REMARKS"), columns));
        }
      }
      if (views.isEmpty() && only != null && !only.isBlank()) {
        throw new IllegalArgumentException("No view " + only + "; there are " + String.join(", ", viewNames()));
      }
      return views;
    } catch (SQLException e) {
      throw new IllegalStateException("Could not read the views of " + schema + ": " + e.getMessage(), e);
    }
  }

  /** The names of the views, lower case. */
  public Set<String> viewNames() {
    try (var connection = reader.getConnection();
         var tables = connection.getMetaData().getTables(null, stored(connection, schema), "%",
             new String[] {"VIEW"})) {
      var names = new TreeSet<String>();
      while (tables.next()) {
        names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
      }
      return names;
    } catch (SQLException e) {
      throw new IllegalStateException("Could not read the views of " + schema + ": " + e.getMessage(), e);
    }
  }

  /** One SELECT over the views, read-only, as the reader login; an IllegalArgumentException saying what is wrong with it. */
  public Result query(String sql) {
    guard.check(sql, viewNames());
    try (var connection = reader.getConnection()) {
      var autoCommit = connection.getAutoCommit();
      var readOnly = connection.isReadOnly();
      var previousSchema = connection.getSchema();
      try {
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
        var postgres = isPostgres(connection);
        try (var setup = connection.createStatement()) {
          if (postgres) {
            setup.execute("set transaction read only");
            setup.execute("set local statement_timeout = " + timeout.toMillis());
          }
        }
        connection.setSchema(stored(connection, schema));
        try (var statement = connection.createStatement()) {
          statement.setQueryTimeout((int) Math.max(1, timeout.toSeconds()));
          statement.setMaxRows(maxRows + 1);
          try (var rs = statement.executeQuery(sql)) {
            var meta = rs.getMetaData();
            var columns = new ArrayList<String>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
              columns.add(meta.getColumnLabel(i).toLowerCase(Locale.ROOT));
            }
            var rows = new ArrayList<List<Object>>();
            var truncated = false;
            while (rs.next()) {
              if (rows.size() == maxRows) {
                truncated = true;
                break;
              }
              var row = new ArrayList<Object>(columns.size());
              for (int i = 1; i <= columns.size(); i++) {
                row.add(value(rs.getObject(i)));
              }
              rows.add(row);
            }
            return new Result(columns, rows, rows.size(), truncated, truncated
                ? "Only the first " + maxRows + " rows: aggregate (count, group by) or filter for the rest" : null);
          }
        }
      } finally {
        // The transaction first: any statement after it would open another, read-only.
        connection.rollback();
        connection.setReadOnly(readOnly);
        connection.setAutoCommit(autoCommit);
        connection.setSchema(previousSchema);
      }
    } catch (SQLException e) {
      throw new IllegalArgumentException("The database refused it: " + SqlGuard.firstLine(e), e);
    }
  }

  /** A value as JSON carries it: numbers, booleans and text as they are, dates as ISO text, long text cut. */
  static Object value(Object value) throws SQLException {
    if (value == null || value instanceof Number || value instanceof Boolean) {
      return value;
    }
    if (value instanceof java.sql.Date date) {
      return date.toLocalDate().toString();
    }
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toInstant().toString();
    }
    if (value instanceof Array array) {
      return List.of((Object[]) array.getArray()).stream().map(String::valueOf).toList();
    }
    var text = String.valueOf(value);
    return text.length() > MAX_CELL ? text.substring(0, MAX_CELL) + "…" : text;
  }

  static boolean isPostgres(Connection connection) throws SQLException {
    return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres");
  }

  /** The schema's name as the database stores it: H2 upper-cases what was not quoted, PostgreSQL lower-cases it. */
  static String stored(Connection connection, String schema) throws SQLException {
    var meta = connection.getMetaData();
    return meta.storesUpperCaseIdentifiers() ? schema.toUpperCase(Locale.ROOT) : schema.toLowerCase(Locale.ROOT);
  }
}
