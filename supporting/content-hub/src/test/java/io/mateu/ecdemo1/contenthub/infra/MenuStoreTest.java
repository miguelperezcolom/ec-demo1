package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.menu.DefaultMenus;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuEntry;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgMenuRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The menus' store against PostgreSQL: one document per site, the seed stored only once. */
class MenuStoreTest {

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
    void the_seed_is_idempotent_and_each_site_keeps_its_own_menu() {
        var repo = new PgMenuRepository(jdbc);
        assertThat(repo.saveIfAbsent(DefaultMenus.intranet())).isTrue();
        assertThat(repo.saveIfAbsent(DefaultMenus.intranet())).isFalse();

        var intranet = repo.find(SiteMenu.INTRANET).orElseThrow();
        intranet.add("rrhh", null, Map.of("es", "RRHH", "en", "HR"), null, Audience.in(Attribute.DEPARTMENT, "RRHH"), true);
        intranet.move("porAprobar", "rrhh", 0);
        repo.save(intranet);
        assertThat(repo.saveIfAbsent(DefaultMenus.intranet())).as("an administrator's menu is left alone").isFalse();

        var newsletter = SiteMenu.empty("newsletter-n1");
        newsletter.add("archivo", null, Map.of("es", "Archivo"), new MenuTarget("NEWSLETTER_ARCHIVE", "n1"), null, true);
        repo.save(newsletter);

        var read = repo.find(SiteMenu.INTRANET).orElseThrow();
        assertThat(read.children("rrhh")).extracting(MenuEntry::id).containsExactly("porAprobar");
        assertThat(read.find("rrhh").orElseThrow().audience()).isEqualTo(Audience.in(Attribute.DEPARTMENT, "RRHH"));
        assertThat(read.find("rrhh").orElseThrow().label("en", "es")).isEqualTo("HR");
        assertThat(repo.siteIds()).containsExactly(SiteMenu.INTRANET, "newsletter-n1");
        assertThat(repo.find("newsletter-n1").orElseThrow().entries()).singleElement()
                .satisfies(e -> assertThat(e.target().kind()).isEqualTo("NEWSLETTER_ARCHIVE"));
        assertThat(repo.find("nobody")).isEmpty();
    }
}
