package io.mateu.ecdemo1.contenthub.domain;

import io.mateu.ecdemo1.contenthub.domain.collaboration.Discussion;
import io.mateu.ecdemo1.contenthub.domain.collaboration.Likes;
import io.mateu.ecdemo1.contenthub.domain.shared.CollaborationEvents;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscussionTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void a_comment_is_stripped_and_announced() {
        var d = Discussion.of("item");
        var c = d.post("c1", "ana", "Ana", "  Hola  ", NOW);
        assertThat(c.text()).isEqualTo("Hola");
        assertThat(d.shown()).containsExactly(c);
        assertThat(d.popEvents()).containsExactly(new CollaborationEvents.CommentPosted("item", "c1", "ana"));
    }

    @Test
    void an_empty_or_too_long_comment_is_refused() {
        var d = Discussion.of("item");
        assertThatThrownBy(() -> d.post("c1", "ana", "Ana", "   ", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> d.post("c1", "ana", "Ana", null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> d.post("c1", "ana", "Ana", "x".repeat(Discussion.MAX_LENGTH + 1), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(d.comments()).isEmpty();
    }

    @Test
    void closed_it_takes_no_more_comments_but_keeps_the_ones_it_has() {
        var d = Discussion.of("item");
        d.post("c1", "ana", "Ana", "Hola", NOW);
        d.close(true, "eva", NOW);
        assertThat(d.closedBy()).isEqualTo("eva");
        assertThatThrownBy(() -> d.post("c2", "ana", "Ana", "Otra", NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(d.shown()).hasSize(1);
        d.close(false, "eva", NOW);
        assertThat(d.closedBy()).isNull();
        d.post("c2", "ana", "Ana", "Otra", NOW);
        assertThat(d.shown()).hasSize(2);
    }

    @Test
    void hiding_keeps_the_comment_and_says_who_and_when() {
        var d = Discussion.of("item");
        d.post("c1", "ana", "Ana", "Hola", NOW);
        d.post("c2", "ana", "Ana", "Otra", NOW);
        d.popEvents();
        var later = NOW.plusSeconds(60);
        d.hide("c1", "eva", later);

        assertThat(d.shown()).extracting(Discussion.Comment::id).containsExactly("c2");
        assertThat(d.shownCount()).isEqualTo(1);
        var hidden = d.comments().getFirst();
        assertThat(hidden.hidden()).isTrue();
        assertThat(hidden.hiddenBy()).isEqualTo("eva");
        assertThat(hidden.hiddenAt()).isEqualTo(later);
        assertThat(d.popEvents()).containsExactly(new CollaborationEvents.CommentModerated("item", "c1", true, "eva"));
        assertThatThrownBy(() -> d.hide("c1", "eva", later)).isInstanceOf(IllegalStateException.class);

        d.restore("c1", "eva");
        assertThat(d.shown()).hasSize(2);
        assertThat(d.comments().getFirst().hiddenBy()).isNull();
        assertThatThrownBy(() -> d.restore("c1", "eva")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> d.hide("nope", "eva", later)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void one_like_per_reader_toggled() {
        var likes = Likes.of("item");
        assertThat(likes.toggle("ana")).isTrue();
        assertThat(likes.toggle("klaus")).isTrue();
        assertThat(likes.count()).isEqualTo(2);
        assertThat(likes.likedBy("ana")).isTrue();
        assertThat(likes.toggle("ana")).isFalse();
        assertThat(likes.likedBy("ana")).isFalse();
        assertThat(likes.count()).isEqualTo(1);
    }
}
