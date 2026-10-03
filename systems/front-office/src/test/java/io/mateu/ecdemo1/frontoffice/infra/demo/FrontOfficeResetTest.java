package io.mateu.ecdemo1.frontoffice.infra.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.demoreset.ConsumerPause;
import io.mateu.ecdemo1.demoreset.DemoReset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * The front office's own reset, against its real schema (schema.sql on PostgreSQL, seeded as it starts)
 * — and the demo-reset auto-configuration, compiled against Boot 3, loading on this Boot 4 app: the
 * guests and stays go, every room is free, what is set up stays.
 */
@SpringBootTest(properties = "demo-reset.settle=0s")
class FrontOfficeResetTest {

  static final GenericContainer<?> postgres = new GenericContainer<>("postgres:16-alpine")
      .withEnv(Map.of("POSTGRES_USER", "fo", "POSTGRES_PASSWORD", "fo", "POSTGRES_DB", "front_office"))
      .withExposedPorts(5432)
      .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    postgres.start();
    registry.add("spring.datasource.url",
        () -> "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/front_office");
    registry.add("spring.datasource.username", () -> "fo");
    registry.add("spring.datasource.password", () -> "fo");
  }

  @AfterAll
  static void stop() {
    postgres.stop();
  }

  @Autowired DemoReset reset;
  @Autowired ObjectProvider<ConsumerPause> consumers;
  @Autowired JdbcTemplate jdbc;

  int count(String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }

  @Test
  void theGuestsAndStaysGoEveryRoomIsFreeWhatIsSetUpStays() {
    var rooms = count("select count(*) from room");
    var charges = count("select count(*) from charge_catalog_item");
    var addOns = count("select count(*) from add_on_catalog_item");
    var automations = count("select count(*) from automation");
    assertThat(count("select count(*) from guest")).isPositive();
    assertThat(count("select count(*) from stay")).isPositive();
    assertThat(count("select count(*) from folio_line")).isPositive();

    var outcome = reset.run();
    reset.run(); // idempotent

    for (var table : List.of("guest", "stay", "stay_companion", "stay_incident", "folio", "folio_line", "walk_in")) {
      assertThat(count("select count(*) from " + table)).as(table).isZero();
    }
    assertThat(count("select count(*) from room")).isEqualTo(rooms).isPositive();
    assertThat(count("select count(*) from room where occupancy <> 'FREE'")).isZero();
    assertThat(count("select count(*) from charge_catalog_item")).isEqualTo(charges);
    assertThat(count("select count(*) from add_on_catalog_item")).isEqualTo(addOns);
    assertThat(count("select count(*) from automation")).isEqualTo(automations);
    assertThat(outcome.service()).isEqualTo("front-office");
    // no broker here: no listeners to pause
    assertThat(consumers.stream()).isEmpty();
  }
}
