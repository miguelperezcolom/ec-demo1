package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.PrimaryColumn;
import io.mateu.uidl.data.Status;

/** A comment in the moderators' list; id is {@code itemId~commentId}. */
public record CommentRow(@Hidden String id, @Label("Comentario") @PrimaryColumn(caption = "item") String text,
                         @Hidden @Label("Contenido") String item, @Label("Autor") String author, @Label("Cuándo") String when,
                         @Label("Estado") Status state) {
}
