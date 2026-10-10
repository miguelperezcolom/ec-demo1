package io.mateu.ecdemo1.agentsql;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * What an agent's SQL may be before it reaches the database: one SELECT (a WITH, a UNION… are
 * SELECTs too), reading only the views of the agent schema, calling no function that reaches outside
 * the data. Defence in depth and clear errors for the model: what actually keeps the data safe is the
 * login the query runs as, which can read those views and nothing else, in a read-only transaction.
 */
public final class SqlGuard {

  /**
   * Functions no question about the data needs and that reach the server, its files, its settings or
   * other databases — by prefix, lower case. Whatever slips past still runs as the reader login.
   */
  static final List<String> FORBIDDEN_FUNCTIONS = List.of("pg_", "set_config", "current_setting", "dblink",
      "lo_", "query_to_xml", "table_to_xml", "cursor_to_xml", "database_to_xml", "schema_to_xml", "txid_",
      "file_", "link_", "copy", "load_file", "sleep", "benchmark");

  /** A call: a name, maybe qualified, and its opening parenthesis — wherever it is in the statement. */
  static final Pattern CALL = Pattern.compile("(?:\\w+\\.)?(\\w+)\\s*\\(");

  final String schema;

  public SqlGuard(String schema) {
    this.schema = schema.toLowerCase(Locale.ROOT);
  }

  /** The views the statement reads, once it is known to be allowed; an {@link IllegalArgumentException} saying why not. */
  public Set<String> check(String sql, Set<String> views) {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("Empty SQL: send one SELECT over the views describe lists");
    }
    Statements statements;
    try {
      statements = CCJSqlParserUtil.parseStatements(sql);
    } catch (JSQLParserException e) {
      throw new IllegalArgumentException("Not valid SQL: " + firstLine(e));
    }
    if (statements.size() != 1) {
      throw new IllegalArgumentException("Exactly one statement, please: " + statements.size() + " were sent");
    }
    if (!(statements.get(0) instanceof Select select)) {
      throw new IllegalArgumentException("Only SELECT: this is read-only, and "
          + statements.get(0).getClass().getSimpleName().toUpperCase(Locale.ROOT) + " is not one");
    }
    var called = CALL.matcher(sql);
    while (called.find()) {
      var name = called.group(1).toLowerCase(Locale.ROOT);
      if (FORBIDDEN_FUNCTIONS.stream().anyMatch(name::startsWith)) {
        throw new IllegalArgumentException("Function " + called.group(1) + " is not allowed");
      }
    }
    var read = new HashSet<String>();
    for (var table : new TablesNamesFinder<Void>().getTables((net.sf.jsqlparser.statement.Statement) select)) {
      read.add(view(table, views));
    }
    return read;
  }

  /** The view a table reference names — qualified with the agent schema or not — or why it may not be read. */
  String view(String reference, Set<String> views) {
    var parts = reference.replace("\"", "").toLowerCase(Locale.ROOT).split("\\.");
    var name = parts[parts.length - 1];
    var qualifier = parts.length > 1 ? parts[parts.length - 2] : null;
    if ((qualifier == null || qualifier.equals(schema)) && views.contains(name)) {
      return name;
    }
    throw new IllegalArgumentException("Only the views of " + schema + " can be read, and " + reference
        + " is not one of them: " + String.join(", ", views.stream().sorted().toList()));
  }

  static String firstLine(Exception e) {
    var cause = e.getCause() != null ? e.getCause() : e;
    var message = String.valueOf(cause.getMessage());
    var newline = message.indexOf('\n');
    return newline < 0 ? message : message.substring(0, newline);
  }
}
