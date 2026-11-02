package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.UsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PgUsageRepository implements UsageRepository {

    final JdbcTemplate jdbc;

    @Override
    public void record(Event e) {
        jdbc.update("insert into ch_usage_event (kind, at, user_id, profile_hash, item_id, query, results) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                e.kind().name(), Sql.ts(e.at()), e.userId(), e.profileHash(), e.itemId(), e.query(), e.results());
        if (e.kind() == Kind.ITEM_VIEW) {
            jdbc.update("insert into ch_item_reader (item_id, user_id, views, first_at, last_at) values (?, ?, 1, ?, ?) "
                            + "on conflict (item_id, user_id) do update set views = ch_item_reader.views + 1, last_at = excluded.last_at",
                    e.itemId(), e.userId(), Sql.ts(e.at()), Sql.ts(e.at()));
        }
    }

    @Override
    public Optional<LocalDate> lastAggregatedDay() {
        return Optional.ofNullable(jdbc.queryForObject("select max(day) from ch_usage_daily", Date.class)).map(Date::toLocalDate);
    }

    @Override
    public Optional<Instant> firstEvent() {
        return Optional.ofNullable(jdbc.queryForObject("select min(at) from ch_usage_event", Timestamp.class)).map(Timestamp::toInstant);
    }

    @Override
    @Transactional
    public void aggregate(LocalDate day, Instant from, Instant to) {
        var date = Date.valueOf(day);
        jdbc.update("delete from ch_usage_daily where day = ?", date);
        // [metric, what it counts]: a total, under the key ""
        for (var a : List.of(
                new String[]{Metric.VIEWS, "count(*)", "kind = 'ITEM_VIEW'"},
                new String[]{Metric.SEARCHES, "count(*)", "kind = 'SEARCH'"},
                new String[]{Metric.SEARCHES_EMPTY, "count(*)", "kind = 'SEARCH' and results = 0"},
                new String[]{Metric.HOME_VISITS, "count(*)", "kind = 'HOME_VISIT'"},
                new String[]{Metric.READERS, "count(distinct user_id)", "user_id is not null"})) {
            jdbc.update("insert into ch_usage_daily (day, metric, key, value) select ?, ?, '', " + a[1]
                    + " from ch_usage_event where " + a[2] + " and at >= ? and at < ? having " + a[1] + " > 0",
                    date, a[0], Sql.ts(from), Sql.ts(to));
        }
        // [metric, key column, filter]: a figure per key
        for (var a : List.of(
                new String[]{Metric.VIEWS_BY_ITEM, "item_id", "kind = 'ITEM_VIEW'"},
                new String[]{Metric.SEARCHES_BY_QUERY, "query", "kind = 'SEARCH'"},
                new String[]{Metric.SEARCHES_EMPTY_BY_QUERY, "query", "kind = 'SEARCH' and results = 0"})) {
            jdbc.update("insert into ch_usage_daily (day, metric, key, value) select ?, ?, " + a[1] + ", count(*) "
                    + "from ch_usage_event where " + a[2] + " and at >= ? and at < ? group by " + a[1],
                    date, a[0], Sql.ts(from), Sql.ts(to));
        }
    }

    @Override
    public int purgeBefore(Instant at) {
        return jdbc.update("delete from ch_usage_event where at < ?", Sql.ts(at));
    }

    @Override
    public Set<String> activeReaders(Instant since) {
        return new HashSet<>(jdbc.queryForList("select distinct user_id from ch_usage_event "
                + "where user_id is not null and at >= ?", String.class, Sql.ts(since)));
    }

    @Override
    public long total(String metric, LocalDate from) {
        var total = jdbc.queryForObject("select coalesce(sum(value), 0) from ch_usage_daily where metric = ? and day >= ?",
                Long.class, metric, Date.valueOf(from));
        return total == null ? 0 : total;
    }

    @Override
    public Map<LocalDate, Long> perDay(String metric, LocalDate from) {
        var out = new LinkedHashMap<LocalDate, Long>();
        jdbc.query("select day, sum(value) from ch_usage_daily where metric = ? and day >= ? group by day order by day",
                rs -> {
                    out.put(rs.getDate(1).toLocalDate(), rs.getLong(2));
                }, metric, Date.valueOf(from));
        return out;
    }

    @Override
    public List<Ranked> top(String metric, LocalDate from, int limit) {
        return jdbc.query("select key, sum(value) as n from ch_usage_daily where metric = ? and day >= ? "
                        + "group by key order by n desc, key limit ?",
                (rs, i) -> new Ranked(rs.getString(1), rs.getLong(2)), metric, Date.valueOf(from), limit);
    }

    @Override
    public ItemStats itemStats(String itemId) {
        return jdbc.queryForObject("select coalesce(sum(views), 0), count(*) from ch_item_reader where item_id = ?",
                (rs, i) -> new ItemStats(rs.getLong(1), rs.getLong(2)), itemId);
    }
}
