package io.mateu.ecdemo1.integrations.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * purge-engine and the Demo page's reading of the runs, against the engine's tables as EventConductor
 * 2.23 has them (the columns that matter here; checked against ec1's workflow database).
 */
@Testcontainers
class EngineDatabaseTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    JdbcTemplate jdbc;
    EngineDatabase engine;

    @BeforeEach
    void engineTables() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        engine = new EngineDatabase(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)));
        jdbc.execute("""
                drop table if exists process_entity, step_execution_entity, form_execution_entity, process_lock,
                  process_lock_waiter, resource_entity, log_message_entity, sync_invocation, process_trace_context,
                  process_index, received_task, task_override, workflow_definition_entity cascade;
                create table process_entity (id varchar primary key, business_key varchar, status varchar, created timestamp,
                  finished timestamp, completion_percentage int not null default 0, variables text, workflow_definition_id varchar);
                create table step_execution_entity (id varchar primary key, process_id varchar, step_id varchar, step_type varchar,
                  status varchar, attempt_count int not null default 0, started_at timestamp, finished_at timestamp, _order bigint not null default 0);
                create table form_execution_entity (id varchar primary key, process_id varchar, step_id varchar, user_id varchar, status varchar);
                create table process_lock (id varchar primary key, holder_process_id varchar);
                create table process_lock_waiter (id varchar primary key, process_id varchar);
                create table resource_entity (id varchar primary key, process_id varchar);
                create table log_message_entity (id varchar primary key, process_id varchar);
                create table sync_invocation (id varchar primary key, process_id varchar);
                create table process_trace_context (process_id varchar primary key);
                create table process_index (process_id varchar primary key, business_key varchar);
                create table received_task (id varchar primary key, process_id varchar);
                create table task_override (id varchar primary key);
                create table workflow_definition_entity (id varchar primary key);
                """);
        for (var p : new String[]{"old-1", "old-2", "reset"}) {
            jdbc.update("insert into process_entity (id, business_key, status, created, workflow_definition_id, variables) values (?, ?, ?, now(), ?, ?)",
                    p, "k-" + p, "RUNNING", p.equals("reset") ? "reset-demo" : "proyectar-reserva",
                    "[{\"name\":\"launchedBy\",\"value\":\"Ana\"},{\"name\":\"processKey\",\"value\":\"k-" + p + "\"}]");
            jdbc.update("insert into step_execution_entity (id, process_id, step_id, step_type, status, _order) values (?, ?, 'start', 'START', 'COMPLETED', 1)", "s-" + p, p);
            jdbc.update("insert into log_message_entity values (?, ?)", "l-" + p, p);
            jdbc.update("insert into process_trace_context values (?)", p);
            jdbc.update("insert into process_index values (?, ?)", p, "k-" + p);
            jdbc.update("insert into resource_entity values (?, ?)", "r-" + p, p);
            jdbc.update("insert into received_task values (?, ?)", "t-" + p, p);
            jdbc.update("insert into sync_invocation values (?, ?)", "i-" + p, p);
            jdbc.update("insert into process_lock_waiter values (?, ?)", "w-" + p, p);
            jdbc.update("insert into process_lock values (?, ?)", "lock-" + p, p);
        }
        jdbc.update("insert into form_execution_entity values ('f-reset', 'reset', 'confirm', 'admin', 'COMPLETED')");
        jdbc.update("insert into form_execution_entity values ('f-old', 'old-1', 'x', null, 'PENDING')");
        jdbc.update("insert into step_execution_entity (id, process_id, step_id, step_type, status, attempt_count, _order) values ('s-reset-2', 'reset', 'confirm', 'USER_TASK', 'COMPLETED', 1, 2)");
        jdbc.update("insert into task_override values ('o1')");
        jdbc.update("insert into workflow_definition_entity values ('reset-demo')");
    }

    int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void everyProcessGoesButTheResetItselfAndWhatIsSetUpStays() {
        var purge = new EnginePurge(engine);

        assertThat(purge.purgeAllBut("reset")).isEqualTo(2);

        assertThat(jdbc.queryForList("select id from process_entity", String.class)).containsExactly("reset");
        for (var table : new String[]{"step_execution_entity", "form_execution_entity", "process_lock_waiter", "resource_entity",
                "log_message_entity", "sync_invocation", "process_trace_context", "process_index", "received_task"}) {
            assertThat(count("select count(*) from " + table + " where process_id <> 'reset'")).as(table).isZero();
            assertThat(count("select count(*) from " + table + " where process_id = 'reset'")).as(table).isPositive();
        }
        assertThat(jdbc.queryForList("select holder_process_id from process_lock", String.class)).containsExactly("reset");
        assertThat(count("select count(*) from task_override")).isZero();
        assertThat(count("select count(*) from workflow_definition_entity")).isEqualTo(1);
    }

    @Test
    void isIdempotentAndNeedsTheProcessToKeep() {
        var purge = new EnginePurge(engine);
        purge.purgeAllBut("reset");

        assertThat(purge.purgeAllBut("reset")).isZero();
        assertThatThrownBy(() -> purge.purgeAllBut(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theLatestRunWithItsStepsWhoLaunchedAndWhoConfirmed() {
        var runs = new ResetRuns(engine, new ObjectMapper());

        var run = runs.latest().orElseThrow();

        assertThat(run.processId()).isEqualTo("reset");
        assertThat(run.open()).isTrue();
        assertThat(run.launchedBy()).isEqualTo("Ana");
        assertThat(run.confirmedBy()).isEqualTo("admin");
        assertThat(run.steps()).extracting(ResetRuns.Step::stepId).containsExactly("start", "confirm");
        assertThat(run.awaitingConfirmation()).isFalse();
        assertThat(runs.byKey("k-reset")).isPresent();

        jdbc.update("update process_entity set status = 'COMPLETED' where id = 'reset'");
        assertThat(runs.open()).isEmpty();
    }
}
