package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.NotificationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PgNotificationLog implements NotificationLog {

    static final RowMapper<Open> OPEN = (rs, i) -> new Open(rs.getString(1), rs.getString(2), Kind.valueOf(rs.getString(3)));
    static final String OPEN_COLUMNS = "select distinct item_id, user_id, kind from ch_notification_sent "
            + "where resolved_at is null and channel = 'IN_APP' ";

    final JdbcTemplate jdbc;

    @Override
    public boolean enqueue(String itemId, int version, Instant at) {
        return jdbc.update("insert into ch_notification_job (item_id, version, created_at) values (?, ?, ?) on conflict do nothing",
                itemId, version, Sql.ts(at)) > 0;
    }

    @Override
    public List<Job> pendingJobs(int limit) {
        return jdbc.query("select item_id, version, attempts from ch_notification_job where done_at is null "
                        + "order by created_at limit ?",
                (rs, i) -> new Job(rs.getString(1), rs.getInt(2), rs.getInt(3)), limit);
    }

    @Override
    public void attempted(Job job) {
        jdbc.update("update ch_notification_job set attempts = attempts + 1 where item_id = ? and version = ?",
                job.itemId(), job.version());
    }

    @Override
    public void finish(Job job, String outcome, Instant at) {
        jdbc.update("update ch_notification_job set done_at = ?, outcome = ? where item_id = ? and version = ?",
                Sql.ts(at), outcome, job.itemId(), job.version());
    }

    @Override
    public Set<String> sentTo(String itemId, int version, Kind kind, Channel channel) {
        return new HashSet<>(jdbc.queryForList("select user_id from ch_notification_sent "
                        + "where item_id = ? and version = ? and kind = ? and channel = ?",
                String.class, itemId, version, kind.name(), channel.name()));
    }

    @Override
    public boolean markSent(String itemId, int version, String userId, Kind kind, Channel channel, Instant at) {
        return jdbc.update("insert into ch_notification_sent (item_id, version, user_id, kind, channel, at) "
                + "values (?, ?, ?, ?, ?, ?) on conflict do nothing",
                itemId, version, userId, kind.name(), channel.name(), Sql.ts(at)) > 0;
    }

    @Override
    public long readersTold(String itemId) {
        var told = jdbc.queryForObject("select count(distinct user_id) from ch_notification_sent "
                + "where item_id = ? and kind = 'PUBLISHED' and channel = 'IN_APP'", Long.class, itemId);
        return told == null ? 0 : told;
    }

    @Override
    public List<Open> openFor(String userId, String itemId, Kind kind) {
        return jdbc.query(OPEN_COLUMNS + "and user_id = ? and item_id = ? and kind = ?", OPEN, userId, itemId, kind.name());
    }

    @Override
    public Set<String> itemsWithOpen() {
        return new HashSet<>(jdbc.queryForList(
                "select distinct item_id from ch_notification_sent where resolved_at is null and channel = 'IN_APP'", String.class));
    }

    @Override
    public List<Open> openOf(String itemId, int limit) {
        return jdbc.query(OPEN_COLUMNS + "and item_id = ? limit ?", OPEN, itemId, limit);
    }

    @Override
    public List<Open> openOlderThan(Instant at, int limit) {
        return jdbc.query(OPEN_COLUMNS + "and at < ? limit ?", OPEN, Sql.ts(at), limit);
    }

    /** Every version: the inbox closes by subject, which has none. */
    @Override
    public void resolved(Open open, Instant at) {
        jdbc.update("update ch_notification_sent set resolved_at = ? where item_id = ? and user_id = ? and kind = ? "
                + "and channel = 'IN_APP' and resolved_at is null", Sql.ts(at), open.itemId(), open.userId(), open.kind().name());
    }
}
