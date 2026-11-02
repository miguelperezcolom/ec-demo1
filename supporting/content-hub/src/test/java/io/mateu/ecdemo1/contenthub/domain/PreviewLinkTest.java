package io.mateu.ecdemo1.contenthub.domain;

import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLinkStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A preview link works until it expires, is revoked or its item is published; its views are kept. */
class PreviewLinkTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    static Item draft(String id) {
        return Item.create(id, ItemType.ARTICLE, "eva", "es", new ItemContent("s1", null,
                Map.of("es", new Translation("Borrador", "", "<p>x</p>")), List.of(), null, null, false, false, null, null), NOW);
    }

    @Test
    void lasts_seven_days_by_default_then_expires() {
        var item = draft("i1");
        var link = PreviewLink.create("l1", item, "eva", null, null, null, NOW);
        assertThat(link.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(link.status(item, NOW.plus(Duration.ofDays(6)))).isEqualTo(PreviewLinkStatus.ACTIVE);
        assertThat(link.status(item, NOW.plus(Duration.ofDays(7)))).isEqualTo(PreviewLinkStatus.EXPIRED);
    }

    @Test
    void lasts_between_one_and_thirty_days() {
        var item = draft("i1");
        assertThatThrownBy(() -> PreviewLink.create("l1", item, "eva", Duration.ofDays(31), null, null, NOW))
                .hasMessageContaining("30");
        assertThatThrownBy(() -> PreviewLink.create("l1", item, "eva", Duration.ZERO, null, null, NOW));
    }

    @Test
    void revoking_ends_it_and_keeps_who_did() {
        var item = draft("i1");
        var link = PreviewLink.create("l1", item, "eva", Duration.ofDays(3), null, null, NOW);
        link.revoke("pablo", NOW.plusSeconds(60));
        link.revoke("otro", NOW.plusSeconds(120));
        assertThat(link.status(item, NOW.plusSeconds(180))).isEqualTo(PreviewLinkStatus.REVOKED);
        assertThat(link.revokedBy()).isEqualTo("pablo");
    }

    @Test
    void publishing_the_item_ends_it_and_a_later_draft_change_needs_a_new_link() {
        var item = draft("i1");
        var link = PreviewLink.create("l1", item, "eva", null, null, null, NOW);
        item.publish("pablo", true, false, NOW.plusSeconds(10));
        assertThat(link.status(item, NOW.plusSeconds(20))).isEqualTo(PreviewLinkStatus.PUBLISHED);
        // published as it is: no draft to show
        assertThatThrownBy(() -> PreviewLink.create("l2", item, "eva", null, null, null, NOW.plusSeconds(30)))
                .hasMessageContaining("No hay borrador");
        // the next draft: a new link, until the next publication
        item.editDraft(item.draft(), NOW.plusSeconds(40));
        var next = PreviewLink.create("l3", item, "eva", null, null, null, NOW.plusSeconds(50));
        assertThat(next.status(item, NOW.plusSeconds(60))).isEqualTo(PreviewLinkStatus.ACTIVE);
        assertThat(link.status(item, NOW.plusSeconds(60))).isEqualTo(PreviewLinkStatus.PUBLISHED);
    }

    @Test
    void is_only_for_its_own_item() {
        var link = PreviewLink.create("l1", draft("i1"), "eva", null, null, null, NOW);
        assertThat(link.status(draft("i2"), NOW)).isEqualTo(PreviewLinkStatus.REVOKED);
        assertThat(link.status(null, NOW)).isEqualTo(PreviewLinkStatus.REVOKED);
    }

    @Test
    void records_views_with_who_and_counts_people() {
        var link = PreviewLink.create("l1", draft("i1"), "eva", null, "h1", "HOTEL=TFS01", NOW);
        link.recordView("ana", NOW.plusSeconds(1));
        link.recordView("ana", NOW.plusSeconds(2));
        link.recordView("luis", NOW.plusSeconds(3));
        assertThat(link.viewCount()).isEqualTo(3);
        assertThat(link.uniqueViewers()).isEqualTo(2);
        assertThat(link.views()).extracting(PreviewLink.View::userId).containsExactly("ana", "ana", "luis");
        assertThat(link.viewAs()).isEqualTo("h1");
        for (int i = 0; i < 250; i++) {
            link.recordView("u" + i, NOW.plusSeconds(10 + i));
        }
        assertThat(link.viewCount()).isEqualTo(253);
        assertThat(link.views()).hasSize(200).last().extracting(PreviewLink.View::userId).isEqualTo("u249");
    }
}
