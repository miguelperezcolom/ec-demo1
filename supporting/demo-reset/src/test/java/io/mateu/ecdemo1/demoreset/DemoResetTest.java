package io.mateu.ecdemo1.demoreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.mateu.workflow.worker.api.TaskContext;
import io.mateu.workflow.worker.api.TaskFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DemoResetTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    JdbcTemplate jdbc;
    TransactionTemplate tx;
    final List<String> events = new ArrayList<>();

    @BeforeEach
    void schema() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("drop table if exists stay, room, guest, kept cascade");
        jdbc.execute("create table room (id text primary key, occupancy text)");
        jdbc.execute("create table guest (id text primary key)");
        jdbc.execute("create table stay (id text primary key, room_id text references room(id), guest_id text references guest(id))");
        jdbc.execute("create table kept (id text primary key)");
        jdbc.update("insert into room values ('101', 'OCCUPIED')");
        jdbc.update("insert into guest values ('g1')");
        jdbc.update("insert into stay values ('s1', '101', 'g1')");
        jdbc.update("insert into kept values ('k1')");
        events.clear();
    }

    ConsumerPause recording(String name) {
        return new ConsumerPause() {
            @Override
            public void pause() {
                events.add("pause " + name);
            }

            @Override
            public void resume() {
                events.add("resume " + name);
            }
        };
    }

    DemoReset reset(DemoResetPlan plan) {
        return new DemoReset(plan, jdbc, tx, List.of(recording("a"), recording("b")), Duration.ZERO);
    }

    int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void emptiesItsOwnTablesRunsItsStatementsAndLeavesTheRest() {
        var plan = DemoResetPlan.truncate("front-office", "guest", "stay", "legacy_table_gone")
                .then("update room set occupancy = 'FREE'");

        var outcome = reset(plan).run();

        assertThat(count("guest")).isZero();
        assertThat(count("stay")).isZero();
        assertThat(count("room")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select occupancy from room", String.class)).isEqualTo("FREE");
        assertThat(count("kept")).isEqualTo(1);
        assertThat(outcome.tables()).containsExactly("guest", "stay");
        assertThat(outcome.summary()).isEqualTo("front-office: guest, stay (+1 statement(s))");
    }

    @Test
    void consumersArePausedAroundItAndResumedInReverse() {
        reset(DemoResetPlan.truncate("x", "guest", "stay")).run();

        assertThat(events).containsExactly("pause a", "pause b", "resume b", "resume a");
    }

    @Test
    void isIdempotent() {
        var reset = reset(DemoResetPlan.truncate("x", "guest", "stay"));
        reset.run();
        reset.run();

        assertThat(count("guest")).isZero();
    }

    @Test
    void aFailureRollsEverythingBackAndStillResumesTheConsumers() {
        var plan = DemoResetPlan.truncate("x", "guest", "stay").then("update no_such_table set a = 1");

        assertThatThrownBy(() -> reset(plan).run()).isInstanceOf(RuntimeException.class);

        assertThat(count("stay")).isEqualTo(1);
        assertThat(count("guest")).isEqualTo(1);
        assertThat(events).containsExactly("pause a", "pause b", "resume b", "resume a");
    }

    @Test
    void refusesWhatIsNotATableName() {
        assertThatThrownBy(() -> DemoResetPlan.truncate("x", "guest; drop table kept"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTaskIsResetAtOneOnTheServicesTopicAndAFailureIsATaskFailure() throws Exception {
        var ok = DemoResetTask.registration("front-office", reset(DemoResetPlan.truncate("x", "guest", "stay")));
        assertThat(ok.ref()).isEqualTo("reset@1");
        assertThat(ok.topic()).isEqualTo("front-office");
        var progress = new ArrayList<String>();
        ok.handler().handle(new DemoResetTask.Input("reset-demo:1", "admin"), context(progress));
        assertThat(progress).containsExactly("x: guest, stay");

        var failing = DemoResetTask.registration("x",
                reset(DemoResetPlan.truncate("x", "guest").then("select * from no_such_table")));
        assertThatThrownBy(() -> failing.handler().handle(new DemoResetTask.Input("k", "a"), context(progress)))
                .isInstanceOf(TaskFailure.class)
                .hasMessageContaining("x: ");
    }

    static TaskContext context(List<String> progress) {
        return new TaskContext() {
            public String taskExecutionId() { return "t"; }
            public String processId() { return "p"; }
            public String workflowDefinitionId() { return "reset-demo"; }
            public String stepId() { return "reset-x"; }
            public boolean isCancelled() { return false; }
            public void progress(String message) { progress.add(message); }
        };
    }
}
