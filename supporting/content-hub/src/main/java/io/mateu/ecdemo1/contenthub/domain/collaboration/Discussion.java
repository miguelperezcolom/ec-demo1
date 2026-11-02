package io.mateu.ecdemo1.contenthub.domain.collaboration;

import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;
import io.mateu.ecdemo1.contenthub.domain.shared.CollaborationEvents;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The comments on one item, and whether it takes comments at all — apart from the item, so commenting
 * never competes with an editor's draft and closing comments acts at once, without a new version.
 *
 * <p>Comments are never deleted: an editor hides one (who and when are kept) and can show it again.
 * Whether the reader may see the item at all is the application's question, through the visibility
 * function, before it ever reaches here.
 */
public class Discussion extends AggregateRoot {

    public static final int MAX_LENGTH = 2000;

    /**
     * One comment. {@code hiddenBy}/{@code hiddenAt}: the editor who hid it, and when; null while shown.
     */
    public record Comment(String id, String authorId, String authorName, String text, Instant at, boolean hidden,
                          String hiddenBy, Instant hiddenAt) {

        Comment hide(String by, Instant now) {
            return new Comment(id, authorId, authorName, text, at, true, by, now);
        }

        Comment restore() {
            return new Comment(id, authorId, authorName, text, at, false, null, null);
        }
    }

    private String itemId;
    private boolean closed;
    private String closedBy;
    private Instant closedAt;
    private List<Comment> comments;

    @SuppressWarnings("unused") // for the store
    private Discussion() {
    }

    public static Discussion of(String itemId) {
        var d = new Discussion();
        d.itemId = itemId;
        d.comments = new ArrayList<>();
        return d;
    }

    public Comment post(String commentId, String authorId, String authorName, String text, Instant now) {
        if (closed) {
            throw new IllegalStateException("Los comentarios de este contenido están cerrados");
        }
        var clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Escribe algo antes de comentar");
        }
        if (clean.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Un comentario tiene como mucho " + MAX_LENGTH + " caracteres");
        }
        var comment = new Comment(commentId, authorId, authorName, clean, now, false, null, null);
        comments.add(comment);
        raise(new CollaborationEvents.CommentPosted(itemId, commentId, authorId));
        return comment;
    }

    /** Hides a comment from the readers; who and when are kept. */
    public void hide(String commentId, String by, Instant now) {
        var c = find(commentId);
        if (c.hidden()) {
            throw new IllegalStateException("Ese comentario ya está oculto");
        }
        replace(c, c.hide(by, now));
        raise(new CollaborationEvents.CommentModerated(itemId, commentId, true, by));
    }

    public void restore(String commentId, String by) {
        var c = find(commentId);
        if (!c.hidden()) {
            throw new IllegalStateException("Ese comentario no está oculto");
        }
        replace(c, c.restore());
        raise(new CollaborationEvents.CommentModerated(itemId, commentId, false, by));
    }

    /** Closes (or reopens) the item to new comments; the ones already there stay as they are. */
    public void close(boolean close, String by, Instant now) {
        if (close == closed) {
            return;
        }
        closed = close;
        closedBy = close ? by : null;
        closedAt = close ? now : null;
    }

    Comment find(String commentId) {
        return comments.stream().filter(c -> c.id().equals(commentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe el comentario " + commentId));
    }

    void replace(Comment old, Comment now) {
        comments.set(comments.indexOf(old), now);
    }

    public String itemId() {
        return itemId;
    }

    public boolean closed() {
        return closed;
    }

    public String closedBy() {
        return closedBy;
    }

    public Instant closedAt() {
        return closedAt;
    }

    /** Every comment, hidden ones included, oldest first: for the editors. */
    public List<Comment> comments() {
        return List.copyOf(comments);
    }

    /** What readers see: the comments not hidden, oldest first. */
    public List<Comment> shown() {
        return comments.stream().filter(c -> !c.hidden()).toList();
    }

    public int shownCount() {
        return (int) comments.stream().filter(c -> !c.hidden()).count();
    }
}
