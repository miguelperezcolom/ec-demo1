package io.mateu.ecdemo1.agentsql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * On a real PostgreSQL, as the services run: the views created at startup, the reader login reading
 * them — and, with the guard out of the way, still nothing else, and nothing written.
 */
@Testcontainers
class AgentSqlPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  static DriverManagerDataSource owner;
  static DriverManagerDataSource reader;
  static AgentSql agentSql;

  @BeforeAll
  static void aServiceWithItsViews() throws Exception {
    owner = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    var jdbc = new JdbcTemplate(owner);
    jdbc.execute("create table guest (id varchar(20) primary key, name varchar(100), nationality varchar(2),"
        + " document varchar(20))");
    jdbc.execute("insert into guest values ('G1', 'Ana García', 'ES', '12345678Z'), ('G2', 'Hans Müller', 'DE', 'X1')");
    jdbc.execute("create role agent_reader login password 'reader'");

    var properties = new AgentSqlProperties();
    properties.setViews("classpath:test-views.sql");
    properties.setUsername("agent_reader");
    new AgentViews(owner, new DefaultResourceLoader(), properties).create();

    reader = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "agent_reader", "reader");
    agentSql = new AgentSql(reader, "agent", Duration.ofSeconds(2), 1);
  }

  @Test
  void theViewsAndWhatTheyMean() {
    var guests = agentSql.describe("guests").get(0);
    assertThat(guests.description()).isEqualTo("The hotel's guests");
    assertThat(guests.columns()).extracting(AgentSql.Column::name).containsExactly("id", "name", "nationality");
    assertThat(guests.columns().get(2).description()).isEqualTo("ISO-2");
  }

  @Test
  void aQueryAnswersAsTheReader() {
    var result = new AgentSql(reader, "agent", Duration.ofSeconds(2), 200)
        .query("select name from guests where nationality = 'ES'");
    assertThat(result.columns()).containsExactly("name");
    assertThat(result.rows()).containsExactly(java.util.List.of("Ana García"));
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void atMostTheLimitAndItSaysSo() {
    var result = agentSql.query("select id from guests order by id");
    assertThat(result.rows()).hasSize(1);
    assertThat(result.truncated()).isTrue();
    assertThat(result.note()).contains("aggregate");
  }

  @Test
  void theReaderCannotReadTheTablesEvenPastTheGuard() {
    var asReader = new JdbcTemplate(reader);
    assertThatThrownBy(() -> asReader.queryForList("select document from public.guest"))
        .rootCause().hasMessageContaining("permission denied");
    assertThatThrownBy(() -> asReader.queryForList("select pg_read_file('/etc/passwd')"))
        .rootCause().hasMessageContaining("permission denied");
    assertThatThrownBy(() -> asReader.queryForList("select set_config('role', '" + POSTGRES.getUsername()
        + "', false)")).rootCause()
        .hasMessageContaining("permission denied");
    assertThatThrownBy(() -> asReader.update("insert into guest values ('G3', 'X', 'FR', null)"))
        .rootCause().hasMessageContaining("permission denied");
  }

  @Test
  void writingIsNotSelecting() {
    assertThatThrownBy(() -> agentSql.query("delete from guests")).hasMessageContaining("Only SELECT");
    assertThatThrownBy(() -> agentSql.query("select * from guest")).hasMessageContaining("Only the views");
  }

  @Test
  void aSlowQueryIsCutShort() {
    var withSleep = "select count(*) from guests a, guests b, guests c, guests d, guests e, guests f, guests g,"
        + " guests h, guests i, guests j, guests k, guests l, guests m, guests n, guests o, guests p, guests q,"
        + " guests r, guests s, guests t, guests u, guests v, guests w, guests x, guests y, guests z, guests aa,"
        + " guests ab, guests ac, guests ad, guests ae, guests af, guests ag, guests ah";
    assertThatThrownBy(() -> agentSql.query(withSleep)).hasMessageContaining("refused");
  }
}
