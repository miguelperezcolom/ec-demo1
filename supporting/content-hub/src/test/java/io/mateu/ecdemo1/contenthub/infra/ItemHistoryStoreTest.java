package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.HistoryAction;
import io.mateu.ecdemo1.contenthub.domain.item.HistoryEntry;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgItemHistoryRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The items' history against PostgreSQL: numbered per item, the drafts kept as they were. */
class ItemHistoryStoreTest {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcTemplate jdbc;

    @BeforeAll
    static void start() {
        PG.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        jdbc.execute((java.sql.Connection c) -> {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("schema.sql"));
            return null;
        });
    }

    @AfterAll
    static void stop() {
        PG.stop();
    }

    @Test
    void appends_numbered_per_item_and_reads_back_the_snapshots() {
        var repo = new PgItemHistoryRepository(jdbc);
        var at = Instant.parse("2026-11-02T09:00:00Z");
        var draft = new ItemContent("noticias", Audience.in(Attribute.HOTEL, "TFS01"),
                Map.of("es", new Translation("Hola", "", "<p>cuerpo</p>")), List.of(), null, null, false, false, null, null)
                .withNotification(true, true);

        assertThat(repo.append(HistoryEntry.of("a", HistoryAction.CREATED, at, "eva", null, null, draft)).seq()).isEqualTo(1);
        assertThat(repo.append(HistoryEntry.of("b", HistoryAction.CREATED, at, "eva", null, null, draft)).seq()).isEqualTo(1);
        assertThat(repo.append(HistoryEntry.of("a", HistoryAction.PUBLISHED, at.plusSeconds(60), "pablo", 1, null, null)).seq())
                .isEqualTo(2);

        var read = repo.of("a");
        assertThat(read).extracting(HistoryEntry::seq, HistoryEntry::action).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, HistoryAction.CREATED),
                org.assertj.core.groups.Tuple.tuple(2, HistoryAction.PUBLISHED));
        assertThat(read.get(0).snapshot()).isEqualTo(draft);
        assertThat(read.get(1).version()).isEqualTo(1);
        assertThat(read.get(1).snapshot()).isNull();
        assertThat(read.get(1).at()).isEqualTo(at.plusSeconds(60));
    }
}
