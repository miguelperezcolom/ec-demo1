package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mateu.ecdemo1.contenthub.application.out.HealthRepository;
import io.mateu.ecdemo1.contenthub.domain.health.Finding;
import io.mateu.ecdemo1.contenthub.domain.health.LinkStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** «Salud del contenido»'s tables (ch_health_*): runs, findings as JSON documents, marks and the URL cache. */
@Repository
@RequiredArgsConstructor
public class PgHealthRepository implements HealthRepository {

    static final TypeReference<Map<String, Integer>> COUNTS = new TypeReference<>() {
    };

    final JdbcTemplate jdbc;

    @Override
    public boolean claimNightly(LocalDate day, String runId, Instant now) {
        return jdbc.update("insert into ch_health_run (id, trigger, nightly_day, started_by, started_at) "
                + "values (?, 'NIGHTLY', ?, 'sistema', ?) on conflict (nightly_day) do nothing",
                runId, Date.valueOf(day), Sql.ts(now)) == 1;
    }

    @Override
    public void startRun(String runId, String trigger, String by, Instant now) {
        jdbc.update("insert into ch_health_run (id, trigger, started_by, started_at) values (?, ?, ?, ?)",
                runId, trigger, by, Sql.ts(now));
    }

    @Override
    public void finishRun(String runId, Map<String, Integer> counts, String error, Instant now) {
        jdbc.update("update ch_health_run set finished_at = ?, counts = ?::jsonb, error = ? where id = ?",
                Sql.ts(now), Json.write(counts), error, runId);
    }

    @Override
    public Optional<Run> lastRun() {
        return jdbc.query("select id, trigger, started_by, started_at, finished_at, counts, error from ch_health_run "
                + "order by started_at desc, finished_at desc nulls first limit 1", (rs, i) -> new Run(rs.getString(1),
                rs.getString(2), rs.getString(3), Sql.instant(rs.getTimestamp(4)), Sql.instant(rs.getTimestamp(5)),
                rs.getString(6) == null ? Map.of() : Json.read(rs.getString(6), COUNTS), rs.getString(7)))
                .stream().findFirst();
    }

    @Override
    @Transactional
    public void replaceFindings(List<Finding> findings, Instant now) {
        var keys = new HashSet<String>();
        for (var f : findings) {
            if (!keys.add(f.key())) {
                continue;
            }
            jdbc.update("insert into ch_health_finding (key, doc, check_name, first_seen, last_seen) values (?, ?::jsonb, ?, ?, ?) "
                    + "on conflict (key) do update set doc = excluded.doc, check_name = excluded.check_name, "
                    + "last_seen = excluded.last_seen", f.key(), Json.write(f), f.check().name(), Sql.ts(now), Sql.ts(now));
        }
        if (keys.isEmpty()) {
            jdbc.update("delete from ch_health_finding");
        } else {
            jdbc.update("delete from ch_health_finding where not (key = any (?::text[]))", Sql.array(keys));
        }
        // a mark on something that is gone is of no use
        jdbc.update("delete from ch_health_snooze s where not exists (select 1 from ch_health_finding f where f.key = s.key)");
    }

    @Override
    public List<StoredFinding> findings() {
        return jdbc.query("select doc, first_seen, last_seen from ch_health_finding order by check_name, first_seen, key",
                (rs, i) -> new StoredFinding(Json.read(rs.getString(1), Finding.class), Sql.instant(rs.getTimestamp(2)),
                        Sql.instant(rs.getTimestamp(3))));
    }

    @Override
    public Optional<StoredFinding> finding(String key) {
        return jdbc.query("select doc, first_seen, last_seen from ch_health_finding where key = ?",
                (rs, i) -> new StoredFinding(Json.read(rs.getString(1), Finding.class), Sql.instant(rs.getTimestamp(2)),
                        Sql.instant(rs.getTimestamp(3))), key).stream().findFirst();
    }

    @Override
    public void removeFinding(String key) {
        jdbc.update("delete from ch_health_finding where key = ?", key);
        jdbc.update("delete from ch_health_snooze where key = ?", key);
    }

    @Override
    public Map<String, Snooze> snoozes() {
        var out = new LinkedHashMap<String, Snooze>();
        jdbc.query("select key, until, by_user, note, at from ch_health_snooze", rs -> {
            out.put(rs.getString(1), new Snooze(rs.getString(1), Sql.instant(rs.getTimestamp(2)), rs.getString(3),
                    rs.getString(4), Sql.instant(rs.getTimestamp(5))));
        });
        return out;
    }

    @Override
    public void snooze(Snooze s) {
        jdbc.update("insert into ch_health_snooze (key, until, by_user, note, at) values (?, ?, ?, ?, ?) "
                        + "on conflict (key) do update set until = excluded.until, by_user = excluded.by_user, "
                        + "note = excluded.note, at = excluded.at",
                s.key(), Sql.ts(s.until()), s.by(), s.note(), Sql.ts(s.at()));
    }

    @Override
    public void unsnooze(String key) {
        jdbc.update("delete from ch_health_snooze where key = ?", key);
    }

    @Override
    public Optional<LinkStatus> cachedLink(String url) {
        return jdbc.query("select url, outcome, http_status, detail, checked_at from ch_health_link where url = ?",
                (rs, i) -> new LinkStatus(rs.getString(1), LinkStatus.Outcome.valueOf(rs.getString(2)), rs.getInt(3),
                        rs.getString(4), Sql.instant(rs.getTimestamp(5))), url).stream().findFirst();
    }

    @Override
    public void cacheLink(LinkStatus s) {
        jdbc.update("insert into ch_health_link (url, outcome, http_status, detail, checked_at) values (?, ?, ?, ?, ?) "
                        + "on conflict (url) do update set outcome = excluded.outcome, http_status = excluded.http_status, "
                        + "detail = excluded.detail, checked_at = excluded.checked_at",
                s.url(), s.outcome().name(), s.httpStatus(), s.detail(), Sql.ts(s.checkedAt()));
    }
}
