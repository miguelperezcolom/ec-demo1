package io.mateu.ecdemo1.demoreset;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The service's own reset: its consumers paused, its tables emptied in one transaction, its consumers
 * resumed. Nobody else's SQL and no restart: the service does it to itself, while it keeps running.
 *
 * <p>Idempotent — emptying what is empty is a no-op — so the engine may run it again: on a retry, or
 * a «Retry from failure» of the whole process.
 */
public class DemoReset {

    private static final Logger log = LoggerFactory.getLogger(DemoReset.class);

    private final DemoResetPlan plan;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final List<ConsumerPause> consumers;
    private final Duration settle;

    public DemoReset(DemoResetPlan plan, JdbcTemplate jdbc, TransactionTemplate transaction,
                     List<ConsumerPause> consumers, Duration settle) {
        this.plan = plan;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.consumers = List.copyOf(consumers);
        this.settle = settle == null ? Duration.ZERO : settle;
    }

    public DemoResetPlan plan() {
        return plan;
    }

    /** What was emptied, for the step's outcome. */
    public record Outcome(String service, List<String> tables, int statements) {
        public String summary() {
            return service + ": " + (tables.isEmpty() ? "no tables" : String.join(", ", tables))
                    + (statements == 0 ? "" : " (+" + statements + " statement(s))");
        }
    }

    public Outcome run() {
        var paused = new ArrayList<ConsumerPause>();
        try {
            for (var consumer : consumers) {
                consumer.pause();
                paused.add(consumer);
            }
            if (!paused.isEmpty() && !settle.isZero()) {
                // What a consumer had already polled is still being handled when pause() returns:
                // give it the time to finish before the tables go.
                Thread.sleep(settle.toMillis());
            }
            var present = plan.tables().stream().filter(this::exists).toList();
            transaction.executeWithoutResult(status -> {
                if (!present.isEmpty()) {
                    jdbc.execute("TRUNCATE TABLE " + String.join(", ", present) + " CASCADE");
                }
                plan.statements().forEach(jdbc::execute);
            });
            var outcome = new Outcome(plan.service(), present, plan.statements().size());
            log.info("Demo reset — {}", outcome.summary());
            return outcome;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before the reset of " + plan.service(), e);
        } finally {
            for (var consumer : paused.reversed()) {
                try {
                    consumer.resume();
                } catch (RuntimeException e) {
                    log.error("Could not resume {} after the demo reset", consumer.describe(), e);
                }
            }
        }
    }

    private boolean exists(String table) {
        return jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class, table);
    }
}
