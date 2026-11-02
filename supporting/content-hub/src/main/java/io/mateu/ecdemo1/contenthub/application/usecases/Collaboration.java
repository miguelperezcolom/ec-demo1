package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.DiscussionRepository;
import io.mateu.ecdemo1.contenthub.application.out.LikesRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.collaboration.Discussion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Comments and likes on items (requirement 1364). A reader only comments on, likes or reads the
 * comments of an item the visibility function lets them see — and never while an editor previews the
 * intranet as someone else («ver como»). Editors moderate: hide a comment (who and when are kept), show
 * it again, close an item to new comments.
 */
@Service
@RequiredArgsConstructor
public class Collaboration {

    final Visibility visibility;
    final DiscussionRepository discussions;
    final LikesRepository likes;
    final DomainEvents events;
    final Clock clock;

    @Transactional
    public Discussion.Comment comment(Reader reader, String itemId, String text) {
        acting(reader, itemId);
        var d = discussions.lockAndGet(itemId);
        var c = d.post("c" + UUID.randomUUID().toString().substring(0, 12), reader.userId(), reader.name(), text,
                clock.instant());
        discussions.save(d);
        events.drain(d);
        return c;
    }

    /** What the item page shows of an item the reader can see. */
    public record Social(List<Discussion.Comment> comments, boolean open, int likes, boolean liked) {
    }

    /** The comments the reader sees (not the hidden ones, oldest first), whether it takes more, its likes. */
    public Social social(Reader reader, String itemId) {
        requireVisible(reader, itemId);
        var d = discussions.get(itemId);
        var l = likes.get(itemId);
        return new Social(d.shown(), !d.closed(), l.count(), l.likedBy(reader.userId()));
    }

    /** @return whether the reader likes it now */
    @Transactional
    public boolean toggleLike(Reader reader, String itemId) {
        acting(reader, itemId);
        var l = likes.lockAndGet(itemId);
        var now = l.toggle(reader.userId());
        likes.save(l);
        events.drain(l);
        return now;
    }

    // ── moderation ──────────────────────────────────────────────────────────────────────────────

    /** A comment with the item it is on, for the moderators' list. */
    public record Moderated(String itemId, Discussion.Comment comment) {
    }

    /** Every comment on every item, hidden ones too, newest first. */
    public List<Moderated> allComments(Reader editor) {
        editor.require(EditorialPermission.EDIT);
        return discussions.withComments().stream()
                .flatMap(d -> d.comments().stream().map(c -> new Moderated(d.itemId(), c)))
                .sorted(Comparator.comparing((Moderated m) -> m.comment().at()).reversed())
                .toList();
    }

    /** One comment, hidden or not, for its moderation page. */
    public Optional<Moderated> moderated(Reader editor, String itemId, String commentId) {
        editor.require(EditorialPermission.EDIT);
        return discussions.get(itemId).comments().stream().filter(c -> c.id().equals(commentId)).findFirst()
                .map(c -> new Moderated(itemId, c));
    }

    public boolean commentsClosed(Reader editor, String itemId) {
        editor.require(EditorialPermission.EDIT);
        return discussions.get(itemId).closed();
    }

    @Transactional
    public void hide(Reader editor, String itemId, String commentId) {
        editor.require(EditorialPermission.EDIT);
        var d = discussions.lockAndGet(itemId);
        d.hide(commentId, editor.userId(), clock.instant());
        discussions.save(d);
        events.drain(d);
    }

    @Transactional
    public void restore(Reader editor, String itemId, String commentId) {
        editor.require(EditorialPermission.EDIT);
        var d = discussions.lockAndGet(itemId);
        d.restore(commentId, editor.userId());
        discussions.save(d);
        events.drain(d);
    }

    /** Closes the item to new comments, or opens it again: at once, not with the next version. */
    @Transactional
    public void closeComments(Reader editor, String itemId, boolean closed) {
        editor.require(EditorialPermission.EDIT);
        var d = discussions.lockAndGet(itemId);
        if (d.closed() == closed) {
            return;
        }
        d.close(closed, editor.userId(), clock.instant());
        discussions.save(d);
    }

    private void acting(Reader reader, String itemId) {
        if (reader.viewingAs() != null) {
            throw new IllegalStateException("En «ver como» no se comenta ni se reacciona");
        }
        requireVisible(reader, itemId);
    }

    private void requireVisible(Reader reader, String itemId) {
        if (!visibility.isVisible(reader.attributes(), itemId)) {
            throw new SecurityException("No existe o no lo puedes ver");
        }
    }
}
