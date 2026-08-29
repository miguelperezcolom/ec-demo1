package io.mateu.ecdemo1.iacp.infra.in.scheduling;

import io.mateu.ecdemo1.iacp.application.out.usage.UsageLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Bounds the usage log. It grows one row per prompt forever otherwise, and while budgets only ever
 * read the current window, the history behind it is dead weight past a point.
 *
 * <p>The retention has a floor the config cannot sensibly go below: it must outlast the longest
 * budget window, or a monthly budget would sum against a month whose early days were already purged
 * and quietly under-count. Ninety days by default leaves generous room over a monthly window.
 */
@Component
@Slf4j
public class UsageRetentionScheduler {

    private final UsageLog usageLog;
    private final Duration retention;

    public UsageRetentionScheduler(UsageLog usageLog,
                                   @Value("${cp.usage.retention-days:90}") long retentionDays) {
        this.usageLog = usageLog;
        // At least a month and a day, whatever the config says — the monthly-budget floor.
        this.retention = Duration.ofDays(Math.max(retentionDays, 32));
    }

    @Scheduled(cron = "${cp.usage.purge-cron:0 30 3 * * *}")
    public void purge() {
        int removed = usageLog.purgeOlderThan(retention);
        if (removed > 0) {
            log.info("Usage log: purged {} row(s) older than {}", removed, retention);
        }
    }
}
