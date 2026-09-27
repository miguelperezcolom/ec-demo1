package io.mateu.ecdemo1.mapping.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One APPROVED version per code and scope, held by the database and not only by the code that
 * approves: a partial unique index over type, CRS code and hotel (the chain as '') where the status
 * is APPROVED. Two approvals racing for the same code used to be able to leave both in force.
 *
 * <p>{@code ddl-auto: update} does not create partial indexes, so it is created here, once the
 * tables exist and before the service takes any work (the Kafka bindings and the web server start
 * after every singleton is ready). Versions already duplicated — from before the index — would make
 * creating it fail, so they are settled first, deterministically: the newest approved version stays
 * in force (highest version, then latest decision, then latest creation, then id), the others are
 * SUPERSEDED, and each one changed is logged. It all runs in one transaction holding a lock that
 * lets readers through but not another writer — a pod of the previous release approving during the
 * rollout waits instead of slipping a duplicate between the clean-up and the index.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MappingSchema implements SmartInitializingSingleton {

    public static final String ONE_APPROVED_INDEX = "mapping_entry_one_approved";

    final JdbcTemplate jdbc;
    final TransactionTemplate transactions;

    record Approved(String id, String type, String sourceCode, String hotelCode, int version, Object decidedAt) {
        String scopeKey() {
            return type + "|" + sourceCode + "|" + Objects.toString(hotelCode, "");
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        ensureOneApprovedPerCodeAndScope();
    }

    public void ensureOneApprovedPerCodeAndScope() {
        transactions.executeWithoutResult(tx -> {
            if (jdbc.queryForObject("select to_regclass('mapping_entry') is not null", Boolean.class) != Boolean.TRUE) {
                return;   // no table yet: nothing to settle, and Hibernate has not been asked to create it
            }
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists (select 1 from pg_indexes where tablename = 'mapping_entry' and indexname = ?)",
                    Boolean.class, ONE_APPROVED_INDEX))) {
                log.info("One approved version per code and scope: index {} already in place", ONE_APPROVED_INDEX);
                return;
            }
            jdbc.execute("lock table mapping_entry in share row exclusive mode");
            var superseded = supersedeDuplicates();
            jdbc.execute("create unique index if not exists " + ONE_APPROVED_INDEX
                    + " on mapping_entry (type, source_code, coalesce(hotel_code, '')) where status = 'APPROVED'");
            log.info("One approved version per code and scope: index {} created; {} duplicate approved version(s) superseded",
                    ONE_APPROVED_INDEX, superseded);
        });
    }

    /** Every code and scope with more than one APPROVED keeps its newest; the rest become SUPERSEDED. */
    int supersedeDuplicates() {
        var approved = jdbc.query("""
                select id, type, source_code, hotel_code, entry_version, decided_at
                from mapping_entry e
                where status = 'APPROVED'
                  and (select count(*) from mapping_entry o
                       where o.status = 'APPROVED' and o.type = e.type and o.source_code = e.source_code
                         and coalesce(o.hotel_code, '') = coalesce(e.hotel_code, '')) > 1
                order by type, source_code, coalesce(hotel_code, ''),
                         entry_version desc, decided_at desc nulls last, created_at desc nulls last, id desc
                """, (rs, i) -> new Approved(rs.getString("id"), rs.getString("type"), rs.getString("source_code"),
                rs.getString("hotel_code"), rs.getInt("entry_version"), rs.getObject("decided_at")));
        var losers = losers(approved);
        for (var loser : losers) {
            jdbc.update("update mapping_entry set status = 'SUPERSEDED' where id = ? and status = 'APPROVED'", loser.id());
            var kept = approved.stream().filter(a -> a.scopeKey().equals(loser.scopeKey())).findFirst().orElseThrow();
            log.warn("Two approved versions of {} {} ({}): kept {} (v{}, decided {}), superseded {} (v{}, decided {})",
                    loser.type(), loser.sourceCode(), loser.hotelCode() == null ? "chain" : loser.hotelCode(),
                    kept.id(), kept.version(), kept.decidedAt(), loser.id(), loser.version(), loser.decidedAt());
        }
        return losers.size();
    }

    /** Given the approved versions ordered newest first within each code and scope: all but the first of each. */
    static List<Approved> losers(List<Approved> newestFirst) {
        var losers = new ArrayList<Approved>();
        String scope = null;
        for (var a : newestFirst) {
            if (a.scopeKey().equals(scope)) {
                losers.add(a);
            }
            scope = a.scopeKey();
        }
        return losers;
    }
}
