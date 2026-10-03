package io.mateu.ecdemo1.integrations.demo;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The engine back to zero, but for one process — the reset-demo that asks it, which goes on: every
 * other process goes, with its steps, forms, locks and their waiters, resources, logs, trace contexts,
 * index entries and sync invocations, and the test worker's received tasks and overrides. What is set
 * up stays: definitions, task contracts, forms, rules, the analytics' rollups, the outbox's history.
 * The same tables as deploy/demo/zero.sh empties (common.sh's ENGINE_TABLES and the history), in one
 * transaction; a table this engine version does not have is skipped. Idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnginePurge {

    /** The rows that belong to a process, by the column that says which: table, column. */
    static final List<String[]> BY_PROCESS = List.of(
            new String[]{"step_execution_entity", "process_id"},
            new String[]{"form_execution_entity", "process_id"},
            new String[]{"process_lock_waiter", "process_id"},
            new String[]{"process_lock", "holder_process_id"},
            new String[]{"resource_entity", "process_id"},
            new String[]{"log_message_entity", "process_id"},
            new String[]{"sync_invocation", "process_id"},
            new String[]{"process_trace_context", "process_id"},
            new String[]{"process_index", "process_id"},
            new String[]{"received_task", "process_id"});

    final EngineDatabase engine;

    /** @return how many processes went */
    public int purgeAllBut(String keptProcessId) {
        if (keptProcessId == null || keptProcessId.isBlank()) {
            throw new IllegalArgumentException("purge-engine needs the process to keep");
        }
        var jdbc = engine.required();
        return engine.transaction().execute(status -> {
            for (var t : BY_PROCESS) {
                if (exists(t[0])) {
                    jdbc.update("delete from " + t[0] + " where " + t[1] + " is distinct from ?", keptProcessId);
                }
            }
            if (exists("message_subscription")) {
                jdbc.update("delete from message_subscription where step_execution_id not in "
                        + "(select id from step_execution_entity where process_id = ?)", keptProcessId);
            }
            if (exists("process_placement")) {
                jdbc.update("delete from process_placement where business_key is distinct from "
                        + "(select business_key from process_entity where id = ?)", keptProcessId);
            }
            if (exists("task_override")) {
                jdbc.update("delete from task_override");
            }
            var processes = jdbc.update("delete from process_entity where id <> ?", keptProcessId);
            log.info("Engine purged for the demo reset: {} process(es) gone, {} kept", processes, keptProcessId);
            return processes;
        });
    }

    private boolean exists(String table) {
        return Boolean.TRUE.equals(engine.required().queryForObject("select to_regclass(?) is not null", Boolean.class, table));
    }
}
