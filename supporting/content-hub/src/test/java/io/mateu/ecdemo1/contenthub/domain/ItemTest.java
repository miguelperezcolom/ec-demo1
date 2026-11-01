package io.mateu.ecdemo1.contenthub.domain;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.media.ExternalMedia;
import io.mateu.ecdemo1.contenthub.domain.navigation.Navigation;
import io.mateu.ecdemo1.contenthub.domain.shared.ContentEvents;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemTest {

    static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");

    static ItemContent content(boolean confirm) {
        return new ItemContent("s1", Audience.EVERYONE, Map.of("es", new Translation("Título", "Resumen", "<p>x</p>")),
                List.of(), null, null, false, confirm, null, null);
    }

    @Test
    void the_workflow_goes_through_review_and_publishing_freezes_a_version() {
        var item = Item.create("i1", ItemType.ARTICLE, "eva", "es", content(false), NOW);
        assertThatThrownBy(() -> item.publish("pablo", false, false, NOW)).hasMessageContaining("revisión");
        item.submitForReview(NOW);
        assertThat(item.status()).isEqualTo(ItemStatus.IN_REVIEW);
        var v1 = item.publish("pablo", false, false, NOW);
        assertThat(v1.number()).isEqualTo(1);
        assertThat(v1.content().publishAt()).isEqualTo(NOW);
        assertThat(item.popEvents()).containsExactly(new ContentEvents.ItemPublished("i1", 1));

        item.editDraft(content(false), NOW);
        assertThat(item.publishedVersion()).contains(v1);
        assertThat(item.status()).isEqualTo(ItemStatus.PUBLISHED);
        item.retire(NOW);
        assertThat(item.publishedVersion()).isEmpty();
    }

    @Test
    void reconfirmation_is_the_publishers_choice() {
        var item = Item.create("i1", ItemType.NOTICE, "eva", "es", content(true), NOW);
        item.publish("pablo", true, false, NOW);
        assertThat(item.confirmationCounts(1)).isTrue();
        item.editDraft(content(true), NOW);
        item.publish("pablo", true, false, NOW);
        assertThat(item.confirmationCounts(1)).as("without reconfirmation the old ones still count").isTrue();
        item.editDraft(content(true), NOW);
        item.publish("pablo", true, true, NOW);
        assertThat(item.confirmationCounts(2)).as("with reconfirmation they stop counting").isFalse();
        assertThat(item.confirmationCounts(3)).isTrue();
    }

    @Test
    void dates_must_be_coherent_and_the_default_language_present() {
        var bad = new ItemContent(null, null, Map.of("en", new Translation("T", "", "")), List.of(), NOW, NOW, false,
                false, null, null);
        var item = Item.create("i1", ItemType.ARTICLE, "eva", "es", bad, NOW);
        assertThatThrownBy(() -> item.publish("p", true, false, NOW)).hasMessageContaining("idioma por defecto");
    }

    @Test
    void the_navigation_refuses_cycles_and_shows_ancestors_of_visible_sections() {
        var nav = Navigation.empty();
        nav.add("a", null, Map.of("es", "A"));
        nav.add("b", "a", Map.of("es", "B"));
        nav.add("c", "b", Map.of("es", "C"));
        nav.add("d", null, Map.of("es", "D"));
        assertThatThrownBy(() -> nav.move("a", "c", 0)).hasMessageContaining("dentro de sí misma");
        assertThat(nav.shown(List.of("c"))).containsExactlyInAnyOrder("c", "b", "a");
        nav.move("d", "a", 0);
        assertThat(nav.children("a")).extracting("id").containsExactly("d", "b");
    }

    @Test
    void external_media_keep_only_provider_and_id() {
        var pasted = "<iframe width=\"560\" src=\"https://www.youtube.com/embed/dQw4w9WgXcQ?si=x\" onload=\"evil()\"></iframe>";
        var yt = ExternalMedia.of(pasted);
        assertThat(yt.provider()).isEqualTo(ExternalMedia.Provider.YOUTUBE);
        assertThat(yt.embedUrl()).contains("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");
        assertThat(ExternalMedia.of("https://youtu.be/dQw4w9WgXcQ").videoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(ExternalMedia.of("https://vimeo.com/76979871").embedUrl()).contains("https://player.vimeo.com/video/76979871");
        assertThat(ExternalMedia.of("https://drive.google.com/file/d/1AbCdEfGhIjKlMn/view?usp=sharing").embedUrl())
                .contains("https://drive.google.com/file/d/1AbCdEfGhIjKlMn/preview");
        assertThat(ExternalMedia.of("https://example.com/x.pdf").embedUrl()).isEmpty();
        assertThatThrownBy(() -> ExternalMedia.of("javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class);
    }
}
