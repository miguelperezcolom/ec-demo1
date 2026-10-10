package ecdemo1.agentsql.autoconfigure;

import io.mateu.ecdemo1.agentsql.AgentSql;
import io.mateu.ecdemo1.agentsql.AgentSqlProperties;
import io.mateu.ecdemo1.agentsql.AgentViews;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * {@link AgentSql} and the {@link AgentViews} that create what it reads, on the service's DataSource
 * — and the agents' queries on the reader login when {@code agent-sql.username} names one: a
 * connection of its own per query, not a pool, at the pace an agent asks.
 *
 * <p>Outside {@code io.mateu} on purpose, as messaging's: Mateu's generated configuration
 * component-scans all of {@code io.mateu} with none of Boot's filters.
 */
@AutoConfiguration(afterName = {
    // Boot 3
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    // Boot 4
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"})
@ConditionalOnProperty(prefix = "agent-sql", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(AgentSqlProperties.class)
public class AgentSqlAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  AgentViews agentViews(DataSource dataSource, ResourceLoader resources, AgentSqlProperties properties) {
    return new AgentViews(dataSource, resources, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  AgentSql agentSql(DataSource dataSource, AgentSqlProperties properties) {
    DataSource reader = dataSource;
    if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
      var url = properties.getUrl();
      if (url == null || url.isBlank()) {
        try (var connection = dataSource.getConnection()) {
          url = connection.getMetaData().getURL();
        } catch (java.sql.SQLException e) {
          throw new IllegalStateException("agent-sql.url is not set and the service's own could not be read", e);
        }
      }
      reader = new DriverManagerDataSource(url, properties.getUsername(), properties.getPassword());
    }
    return new AgentSql(reader, properties.getSchema(), properties.getTimeout(), properties.getMaxRows());
  }
}
