package io.mateu.ecdemo1.booking.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.mateu.ecdemo1.booking.application.usecases.intake.CrsIntake;
import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.StreamBindingsPause;
import io.mateu.workflow.worker.api.TaskContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

/**
 * The CRS's part of the demo's reset (reset-demo), against a real Postgres and broker: its intake
 * paused and resumed, its own tables emptied (and nothing else), the demo bookings seeded once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DemoResetIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7")
            .withTmpFs(java.util.Map.of("/var/lib/redpanda/data", "rw,size=8g"));

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
    }

    static final String BOOKING = """
            {"hotelCode":"PMI01","booking":{"channelCode":"WEB","arrival":"2026-10-05","departure":"2026-10-08",
             "holder":{"firstName":"Ana","lastName":"García","email":"ana@example.com","nationality":"ES"},
             "rooms":[{"roomTypeCode":"DBL","ratePlanCode":"BAR","boardCode":"AD","adults":2,"childrenAges":[],
                       "guests":[{"firstName":"Ana","lastName":"García","type":"Adult"}]}]}}
            """;

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DemoReset reset;
    @Autowired
    CrsIntake intake;
    @Autowired
    DemoTaskHandlers handlers;
    @Autowired
    List<StreamBindingsPause> bindingsPauses;

    @BeforeEach
    void open() {
        intake.resume("test");
    }

    static TaskContext context(String processId) {
        return new TaskContext() {
            public String taskExecutionId() { return "te"; }
            public String processId() { return processId; }
            public String workflowDefinitionId() { return "reset-demo"; }
            public String stepId() { return "step"; }
            public boolean isCancelled() { return false; }
            public void progress(String message) { }
        };
    }

    int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void theResetEmptiesTheBookingsTheAddedRatePlansAndTheOutboxButNotThePause() throws Exception {
        mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isCreated());
        assertThat(count("outbox_message")).isPositive();
        assertThat(count("crs_booking")).isPositive();
        assertThat(jdbc.queryForObject("select to_regclass('catalog_rate_plan') is not null", Boolean.class)).isTrue();
        handlers.pauseIntake(new DemoTaskHandlers.PauseIntake("reset-demo:1", "admin"), context("p1"));

        var outcome = reset.run();

        assertThat(outcome.tables()).contains("crs_booking", "catalog_rate_plan", "outbox_message");
        assertThat(count("crs_booking")).isZero();
        assertThat(count("outbox_message")).isZero();
        assertThat(count("crs_intake_pause")).isEqualTo(1);
        assertThat(bindingsPauses).hasSize(1);
    }

    @Test
    void whilePausedNoBookingIsMadeChangedOrCancelledAndTheDemoPageSeesIt() throws Exception {
        var id = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id").toString();

        var until = handlers.pauseIntake(new DemoTaskHandlers.PauseIntake("reset-demo:2", "admin"), context("p2"));
        // a retry of the same step keeps the pause it took
        assertThat(handlers.pauseIntake(new DemoTaskHandlers.PauseIntake("reset-demo:2", "admin"), context("p2")))
                .isEqualTo(until);

        mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith(
                        "El CRS no admite reservas ahora: la demo se está reseteando (hasta ")));
        mvc.perform(post("/bookings/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"CLI\"}")).andExpect(status().isConflict());
        mvc.perform(get("/demo/intake"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.by").value("admin"))
                .andExpect(jsonPath("$.processKey").value("reset-demo:2"))
                .andExpect(jsonPath("$.until").value(until.intakePausedUntil()));

        handlers.resumeIntake(new DemoTaskHandlers.ForProcess("reset-demo:2"), context("p2"));
        handlers.resumeIntake(new DemoTaskHandlers.ForProcess("reset-demo:2"), context("p2"));
        mvc.perform(get("/demo/intake")).andExpect(jsonPath("$.paused").value(false));
        mvc.perform(post("/bookings/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"CLI\"}")).andExpect(status().isOk());
    }

    @Test
    void aPauseNobodyLiftsEndsByItself() {
        jdbc.update("insert into crs_intake_pause (id, paused_until, paused_by, process_key) values ('crs', ?, 'x', 'k') "
                + "on conflict (id) do update set paused_until = excluded.paused_until",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)));

        assertThat(intake.status().paused()).isFalse();
        intake.ensureOpen();
    }

    @Test
    void theSeedMakesTheDemoBookingsOncePerProcess() {
        var first = handlers.seedDemoBookings(new DemoTaskHandlers.Seed("reset-demo:3", "true"), context("p3"));
        var again = handlers.seedDemoBookings(new DemoTaskHandlers.Seed("reset-demo:3", "true"), context("p3"));

        var ids = List.of(first.seededBookings().split(","));
        assertThat(ids).hasSize(10);
        assertThat(again.seededBookings()).isEqualTo(first.seededBookings());
        assertThat(jdbc.queryForObject("select count(*) from crs_booking where hotel_code = 'MRU01' "
                + "and comments like '%demo-reset:reset-demo:3%'", Integer.class)).isEqualTo(10);
    }

    @Test
    void withoutTheBoxTickedNothingIsSeeded() {
        var before = jdbc.queryForObject("select count(*) from crs_booking", Integer.class);

        assertThat(handlers.seedDemoBookings(new DemoTaskHandlers.Seed("reset-demo:4", "false"), context("p4"))
                .seededBookings()).isEmpty();
        assertThat(handlers.seedDemoBookings(new DemoTaskHandlers.Seed("reset-demo:4", null), context("p4"))
                .seededBookings()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from crs_booking", Integer.class)).isEqualTo(before);
    }
}
