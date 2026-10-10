package io.mateu.ecdemo1.agentsql;

import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * The agent schema, (re)created at every start once the service's own tables exist — after every
 * singleton, so after Hibernate's {@code ddl-auto} and {@code spring.sql.init} alike: the schema, the
 * service's views script (dropping and creating each view, so a changed view is the new one), and,
 * on PostgreSQL with a reader login, that login's right to read those views — and only those.
 *
 * <p>A broken script does not stop the service: the desk keeps working, the agent's SQL tools say
 * there is nothing to read, and the log says why.
 */
public class AgentViews implements SmartInitializingSingleton {

  static final Logger log = LoggerFactory.getLogger(AgentViews.class);
  static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  final DataSource dataSource;
  final ResourceLoader resources;
  final AgentSqlProperties properties;

  public AgentViews(DataSource dataSource, ResourceLoader resources, AgentSqlProperties properties) {
    this.dataSource = dataSource;
    this.resources = resources;
    this.properties = properties;
  }

  @Override
  public void afterSingletonsInstantiated() {
    try {
      create();
    } catch (RuntimeException | SQLException e) {
      log.error("The agent's views could not be created; its SQL tools will find nothing to read", e);
    }
  }

  public void create() throws SQLException {
    var schema = identifier(properties.getSchema());
    var jdbc = new JdbcTemplate(dataSource);
    String platform;
    try (var connection = dataSource.getConnection()) {
      platform = AgentSql.isPostgres(connection) ? "postgresql"
          : connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    }
    jdbc.execute("create schema if not exists " + schema);
    var script = script(platform);
    if (script == null) {
      log.warn("No agent views script for {} at {}", platform, properties.getViews());
    } else {
      new ResourceDatabasePopulator(script).execute(dataSource);
      log.info("Agent views created from {}", script.getDescription());
    }
    var reader = properties.getUsername();
    if (reader == null || reader.isBlank()) {
      if ("postgresql".equals(platform)) {
        log.warn("agent-sql has no reader login: the agent's SQL runs as the service's own user, with only "
            + "the SQL guard between it and every table");
      }
      return;
    }
    if ("postgresql".equals(platform)) {
      var login = identifier(reader);
      var exists = jdbc.queryForObject("select count(*) from pg_roles where rolname = ?", Integer.class, reader);
      if (exists == null || exists == 0) {
        log.warn("The reader login {} does not exist yet: the agent's SQL will be refused until it does", reader);
        return;
      }
      jdbc.execute("grant usage on schema " + schema + " to " + login);
      jdbc.execute("grant select on all tables in schema " + schema + " to " + login);
    }
  }

  org.springframework.core.io.Resource script(String platform) {
    var base = properties.getViews();
    var specific = resources.getResource(base.replaceFirst("\\.sql$", "-" + platform + ".sql"));
    if (specific.exists()) {
      return specific;
    }
    var generic = resources.getResource(base);
    return generic.exists() ? generic : null;
  }

  /** A schema or login name, checked: it goes into DDL as it is. */
  static String identifier(String name) {
    if (name == null || !IDENTIFIER.matcher(name).matches()) {
      throw new IllegalArgumentException("Not a plain SQL identifier: " + name);
    }
    return name;
  }
}
