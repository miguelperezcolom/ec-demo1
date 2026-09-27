package io.mateu.ecdemo1.mapping;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.dictionary.ConcurrentDecisionException;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.outbox.Outbox;
import io.mateu.ecdemo1.mapping.queries.CauseQueries;
import io.mateu.ecdemo1.mapping.queries.DictionaryQueries;
import io.mateu.ecdemo1.mapping.queries.PartnerProfileQueries;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import io.mateu.ecdemo1.mapping.store.MappingSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One APPROVED per code and scope against a real Postgres — only Postgres, no broker: the index
 * created at startup, the duplicates it settles first, two approvals racing, and the dictionary's
 * listing paged by the database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({Dictionary.class, MappingSchema.class, DictionaryQueries.class, CauseQueries.class, PartnerProfileQueries.class,
        SingleApprovedInDatabaseTest.Stubs.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SingleApprovedInDatabaseTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));

    @TestConfiguration
    static class Stubs {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        /** The causes are not what this is about; resolving them is recorded as nothing. */
        @Bean
        Causes causes() {
            return new Causes(null, null, null, null, null, null) {
                @Override
                public void mappingApproved(CodeType type, String hotelCode, String code, String approvedBy) {
                }
            };
        }

        @Bean
        Outbox outbox() {
            return new Outbox(null, null, null) {
                @Override
                public void appendResolution(String subject, String resolvedBy) {
                }
            };
        }
    }

    @Autowired
    Dictionary dictionary;
    @Autowired
    MappingEntryRepository entries;
    @Autowired
    MappingSchema schema;
    @Autowired
    DictionaryQueries queries;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate transactions;

    @BeforeEach
    void empty() {
        jdbc.update("delete from mapping_entry");
    }

    MappingEntry propose(String hotel, String code, String target) {
        return dictionary.propose(new Dictionary.Proposal(CodeType.ROOM_TYPE, hotel, code, target, Map.of(), null, null), "agent");
    }

    List<String> approved(String code) {
        return jdbc.queryForList("select target_code from mapping_entry where source_code = ? and status = 'APPROVED'",
                String.class, code);
    }

    @Test
    void theIndexIsThereFromStartupAndRefusesASecondApprovedVersion() {
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname = ?", Integer.class,
                MappingSchema.ONE_APPROVED_INDEX)).isEqualTo(1);
        dictionary.approve(propose("MRU01", "IDX", "A").getId(), "Ana");
        var other = propose("MRU01", "IDX", "B");
        assertThatThrownBy(() -> jdbc.update("update mapping_entry set status = 'APPROVED' where id = ?", other.getId()))
                .hasMessageContaining(MappingSchema.ONE_APPROVED_INDEX);
        // The chain is a scope of its own, and so is another hotel.
        dictionary.approve(propose(null, "IDX", "C").getId(), "Ana");
        dictionary.approve(propose("PMI01", "IDX", "D").getId(), "Ana");
        assertThat(approved("IDX")).containsExactlyInAnyOrder("A", "C", "D");
    }

    @Test
    void existingDuplicatesAreSettledKeepingTheNewestBeforeTheIndexIsCreated() {
        jdbc.execute("drop index " + MappingSchema.ONE_APPROVED_INDEX);
        insertApproved("old", "DUP", "MRU01", 1, "2026-01-01T00:00:00Z");
        insertApproved("new", "DUP", "MRU01", 2, "2026-01-02T00:00:00Z");
        insertApproved("same-version-later", "TWN", null, 1, "2026-01-03T00:00:00Z");
        insertApproved("same-version-earlier", "TWN", null, 1, "2026-01-02T00:00:00Z");
        insertApproved("alone", "SGL", "MRU01", 1, "2026-01-02T00:00:00Z");

        schema.ensureOneApprovedPerCodeAndScope();

        assertThat(jdbc.queryForList("select id || ':' || status from mapping_entry order by id", String.class))
                .containsExactly("alone:APPROVED", "new:APPROVED", "old:SUPERSEDED",
                        "same-version-earlier:SUPERSEDED", "same-version-later:APPROVED");
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname = ?", Integer.class,
                MappingSchema.ONE_APPROVED_INDEX)).isEqualTo(1);
        schema.ensureOneApprovedPerCodeAndScope();   // and again, at the next start: nothing to do
    }

    void insertApproved(String id, String code, String hotel, int version, String decidedAt) {
        jdbc.update("""
                insert into mapping_entry (id, type, hotel_code, source_code, target_code, status, entry_version, decided_at, created_at)
                values (?, 'ROOM_TYPE', ?, ?, ?, 'APPROVED', ?, cast(? as timestamptz), cast(? as timestamptz))
                """, id, hotel, code, "T-" + id, version, decidedAt, decidedAt);
    }

    @Test
    void theSecondOfTwoRacingApprovalsWithNothingInForceLoses() throws Exception {
        race(propose("MRU01", "RACE1", "A").getId(), propose("MRU01", "RACE1", "B").getId());
        assertThat(approved("RACE1")).containsExactly("A");
    }

    @Test
    void theSecondOfTwoRacingCorrectionsOfTheVersionInForceLoses() throws Exception {
        dictionary.approve(propose("MRU01", "RACE2", "V1").getId(), "Ana");
        race(propose("MRU01", "RACE2", "A").getId(), propose("MRU01", "RACE2", "B").getId());
        assertThat(approved("RACE2")).containsExactly("A");
        assertThat(jdbc.queryForList("select target_code || ':' || status || '@' || entry_version from mapping_entry where source_code = 'RACE2'",
                String.class)).containsExactlyInAnyOrder("V1:SUPERSEDED@1", "A:APPROVED@2", "B:PROPOSED@0");
    }

    /** {@code first} is approved and held uncommitted while {@code second} is approved; then the first commits. */
    void race(String first, String second) throws Exception {
        var firstApproved = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var one = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(tx -> {
            dictionary.approve(first, "Ana");
            firstApproved.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(firstApproved.await(10, TimeUnit.SECONDS)).isTrue();
        var two = CompletableFuture.supplyAsync(() -> dictionary.approve(second, "Luis"));
        Thread.sleep(500);
        assertThat(two).as("the second waits for the first").isNotDone();
        release.countDown();
        one.get(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> two.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(ConcurrentDecisionException.class)
                .hasMessageContaining("was approved at the same moment");
        assertThat(entries.findById(second)).get().extracting(MappingEntry::getStatus).isEqualTo(EntryStatus.PROPOSED);
    }

    @Test
    void theDictionaryIsFilteredOrderedAndPagedByTheDatabase() {
        for (var i = 0; i < 25; i++) {
            var e = propose("MRU01", "P%02d".formatted(i), "X" + i);
            if (i % 5 == 0) dictionary.approve(e.getId(), "Ana");
        }
        propose("PMI01", "OTHER", "Y");
        dictionary.define(new Dictionary.Proposal(CodeType.HOTEL, null, "MRU01", "XMAR", Map.of(), null, null), "Ana");

        var mru = new DictionaryQueries.Filter("MRU01", null, null, "");
        var first = queries.page(mru, PageRequest.of(0, 10));
        assertThat(first.getTotalElements()).isEqualTo(26);   // its 25 and the chain's hotel code, not PMI01's
        // What waits first, then what is in force; by type in CodeType's order (HOTEL before ROOM_TYPE), then code.
        assertThat(first.getContent()).extracting(MappingEntry::getSourceCode)
                .containsExactly("P01", "P02", "P03", "P04", "P06", "P07", "P08", "P09", "P11", "P12");
        var last = queries.page(mru, PageRequest.of(2, 10));
        assertThat(last.getContent()).extracting(e -> e.getStatus() + " " + e.getSourceCode())
                .containsExactly("APPROVED MRU01", "APPROVED P00", "APPROVED P05", "APPROVED P10", "APPROVED P15", "APPROVED P20");

        assertThat(queries.count(new DictionaryQueries.Filter("MRU01", CodeType.HOTEL, null, null))).isEqualTo(1);
        assertThat(queries.count(new DictionaryQueries.Filter("MRU01", null, Set.of(EntryStatus.APPROVED), null))).isEqualTo(6);
        assertThat(queries.count(new DictionaryQueries.Filter("MRU01", null, Set.of(), null))).isZero();
        // The search box: "TYPE CRS PMS scope", case-insensitive, its wildcards taken literally.
        assertThat(queries.count(new DictionaryQueries.Filter(null, null, null, "room_type p1"))).isEqualTo(10);
        assertThat(queries.count(new DictionaryQueries.Filter(null, null, null, "xmar"))).isEqualTo(1);
        assertThat(queries.count(new DictionaryQueries.Filter(null, null, null, "chain"))).isEqualTo(1);
        assertThat(queries.count(new DictionaryQueries.Filter(null, null, null, "%"))).isZero();
        assertThat(queries.window(mru, 3, 4)).extracting(MappingEntry::getSourceCode).containsExactly("P04", "P06", "P07", "P08");
        assertThat(queries.proposals("MRU01", CodeType.ROOM_TYPE)).hasSize(20);
    }

    @Autowired
    CauseQueries causes;
    @Autowired
    PartnerProfileQueries profiles;

    @Test
    void causesAndPartnersArePagedByTheDatabase() {
        jdbc.update("delete from cause");
        jdbc.update("delete from partner_profile");
        jdbc.update("insert into cause (cause_key, type, status, opened_at, openings) values ('MISSING_MAPPING:MRU01:BOARD:AD', 'MISSING_MAPPING', 'RESOLVED', now() - interval '2 days', 1)");
        jdbc.update("insert into cause (cause_key, type, status, opened_at, openings) values ('MISSING_MAPPING:MRU01:BOARD:HB', 'MISSING_MAPPING', 'OPEN', now(), 1)");
        jdbc.update("insert into cause (cause_key, type, status, opened_at, openings) values ('MISSING_PARTNER:NORD', 'MISSING_PARTNER', 'OPEN', now() - interval '1 day', 1)");
        assertThat(causes.page("", PageRequest.of(0, 2)).getContent()).extracting(c -> c.causeKey)
                .containsExactly("MISSING_PARTNER:NORD", "MISSING_MAPPING:MRU01:BOARD:HB");
        assertThat(causes.page("board", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);

        jdbc.update("insert into partner_profile (partner_code, profile_type, pms_profile_id, projected_version) values ('NORD', 'TRAVEL_AGENT', 'TA-1', 1), ('ACME', 'COMPANY', 'C-9', 2)");
        assertThat(profiles.page(null, PageRequest.of(0, 10)).getContent()).extracting(p -> p.partnerCode).containsExactly("ACME", "NORD");
        assertThat(profiles.page("travel", PageRequest.of(0, 10)).getContent()).extracting(p -> p.partnerCode).containsExactly("NORD");
    }
}
