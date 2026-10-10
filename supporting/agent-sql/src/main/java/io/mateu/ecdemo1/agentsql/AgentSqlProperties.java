package io.mateu.ecdemo1.agentsql;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code agent-sql.*}: the schema of the views, the script that (re)creates them at startup, and the
 * reader login the agents' queries run as. With no {@code username} the queries run on the service's
 * own DataSource — fine for the tests on H2, never for a real database: there the guard is all there
 * is between the model and the tables, and the service logs a warning.
 */
@ConfigurationProperties("agent-sql")
public class AgentSqlProperties {

  /** Off: no views, no AgentSql bean, and the service's agent tools are not registered. */
  private boolean enabled = true;
  private String schema = "agent";
  /** Run once the service's own tables exist; {@code views-<platform>.sql} beside it wins when present. */
  private String views = "classpath:agent-sql/views.sql";
  private Duration timeout = Duration.ofSeconds(5);
  private int maxRows = 200;
  /** The reader login: the JDBC URL defaults to the service's own. */
  private String url;
  private String username;
  private String password;

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getSchema() { return schema; }
  public void setSchema(String schema) { this.schema = schema; }
  public String getViews() { return views; }
  public void setViews(String views) { this.views = views; }
  public Duration getTimeout() { return timeout; }
  public void setTimeout(Duration timeout) { this.timeout = timeout; }
  public int getMaxRows() { return maxRows; }
  public void setMaxRows(int maxRows) { this.maxRows = maxRows; }
  public String getUrl() { return url; }
  public void setUrl(String url) { this.url = url; }
  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
}
