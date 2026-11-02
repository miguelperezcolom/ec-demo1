package io.mateu.ecdemo1.contenthub.domain;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiteTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    static ItemContent content() {
        return new ItemContent(null, Audience.EVERYONE, Map.of(), List.of(), null, null, false, false, null, null);
    }

    @Test
    void what_says_no_site_is_the_intranets() {
        assertThat(Site.of(null)).isEqualTo(Site.INTRANET);
        assertThat(Site.of(" ")).isEqualTo(Site.INTRANET);
        assertThat(Item.create("a", ItemType.ARTICLE, "eva", "es", content(), NOW).siteId()).isEqualTo(Site.INTRANET);
        assertThat(Item.create("b", "boletin", ItemType.PAGE, "eva", "es", content(), NOW).siteId()).isEqualTo("boletin");
    }

    @Test
    void a_site_reads_in_its_languages_or_its_first_one() {
        var site = Site.create("boletin", Site.Kind.NEWSLETTER, new Site.Definition("Boletín", "", null,
                List.of("es", "en"), null, null, null, null, true));
        assertThat(site.languageFor("en")).isEqualTo("en");
        assertThat(site.languageFor("de")).isEqualTo("es");
        assertThat(Site.intranet().languageFor("de")).isEqualTo("de");
    }

    @Test
    void ids_names_and_colours_are_checked() {
        assertThatThrownBy(() -> Site.create("Con Espacios", Site.Kind.NEWSLETTER, Site.intranet().definition()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Site.create("intranet", Site.Kind.NEWSLETTER, Site.intranet().definition()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Site.Theme("rojo", null)).hasMessageContaining("#RRGGBB");
        assertThat(new Site.Theme("#AA0000", null).primary()).isEqualTo("#aa0000");
        assertThat(Site.newId("Boletín de RRHH")).matches("boletin-de-rrhh-[0-9a-f]{4}");
    }
}
