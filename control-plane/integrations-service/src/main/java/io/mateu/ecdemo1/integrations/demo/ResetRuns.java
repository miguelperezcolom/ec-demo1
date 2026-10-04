package io.mateu.ecdemo1.integrations.demo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The reset-demo runs as the engine has them: the latest, its steps and who launched and confirmed it.
 * Read from the engine's database — the engine has no API for a process's steps.
 */
@Component
@RequiredArgsConstructor
public class ResetRuns {

    public static final String DEFINITION = "reset-demo";

    /** A run that is not over: a new one waits for it — finished, cancelled, or retried to its end. */
    static final Set<String> OPEN = Set.of("PENDING", "RUNNING", "PAUSED", "ERROR");

    public record Step(String stepId, String type, String status, int attempts, Instant startedAt, Instant finishedAt) {
    }

    public record Run(String processId, String businessKey, String status, Instant created, Instant finished,
                      int completion, Map<String, String> variables, String confirmedBy, List<Step> steps) {

        public boolean open() {
            return OPEN.contains(status);
        }

        public boolean failed() {
            return "ERROR".equals(status);
        }

        public String launchedBy() {
            return variables.getOrDefault("launchedBy", "—");
        }

        /** Waiting for a person to confirm it in the inbox. */
        public boolean awaitingConfirmation() {
            return steps.stream().anyMatch(s -> "confirm".equals(s.stepId()) && !terminal(s.status()));
        }

        /** The person said no: the run ended at «not-confirmed», having changed nothing. */
        public boolean notConfirmed() {
            return steps.stream().anyMatch(s -> "not-confirmed".equals(s.stepId()) && "COMPLETED".equals(s.status()));
        }

        public Optional<Step> failedStep() {
            return steps.stream().filter(s -> "ERROR".equals(s.status()) || "TIMEOUT".equals(s.status())).reduce((a, b) -> b);
        }
    }

    static boolean terminal(String status) {
        return Set.of("COMPLETED", "CANCELLED", "ERROR", "TIMEOUT").contains(status);
    }

    final EngineDatabase engine;
    final ObjectMapper objectMapper;

    public boolean available() {
        return engine.jdbc().isPresent();
    }

    public Optional<Run> latest() {
        return engine.jdbc().flatMap(jdbc -> jdbc.query("""
                select id, business_key, status, created, finished, completion_percentage, variables
                from process_entity where workflow_definition_id = ? order by created desc limit 1""",
                (rs, i) -> run(rs), DEFINITION).stream().findFirst());
    }

    /** The open run, if any — what a new launch must wait for. */
    public Optional<Run> open() {
        return latest().filter(Run::open);
    }

    public Optional<Run> byKey(String businessKey) {
        return engine.jdbc().flatMap(jdbc -> jdbc.query("""
                select id, business_key, status, created, finished, completion_percentage, variables
                from process_entity where business_key = ?""", (rs, i) -> run(rs), businessKey).stream().findFirst());
    }

    Run run(ResultSet rs) throws SQLException {
        var id = rs.getString("id");
        var jdbc = engine.required();
        var steps = jdbc.query("""
                select step_id, step_type, status, attempt_count, started_at, finished_at
                from step_execution_entity where process_id = ? order by _order, started_at""",
                (s, i) -> new Step(s.getString("step_id"), s.getString("step_type"), s.getString("status"),
                        s.getInt("attempt_count"), instant(s.getTimestamp("started_at")), instant(s.getTimestamp("finished_at"))),
                id);
        var confirmedBy = jdbc.query("select user_id from form_execution_entity where process_id = ? and user_id is not null",
                (s, i) -> s.getString(1), id).stream().findFirst().orElse(null);
        return new Run(id, rs.getString("business_key"), rs.getString("status"), instant(rs.getTimestamp("created")),
                instant(rs.getTimestamp("finished")), rs.getInt("completion_percentage"), variables(rs.getString("variables")),
                confirmedBy, steps);
    }

    Map<String, String> variables(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            List<Map<String, String>> list = objectMapper.readValue(json, new TypeReference<>() {
            });
            var vars = new java.util.LinkedHashMap<String, String>();
            list.forEach(v -> {
                if (v.get("name") != null && v.get("value") != null) {
                    vars.put(v.get("name"), v.get("value"));
                }
            });
            return vars;
        } catch (Exception e) {
            return Map.of();
        }
    }

    static Instant instant(Timestamp t) {
        // The engine writes its timestamps in UTC into columns without a zone.
        return t == null ? null : t.toLocalDateTime().toInstant(java.time.ZoneOffset.UTC);
    }
}
