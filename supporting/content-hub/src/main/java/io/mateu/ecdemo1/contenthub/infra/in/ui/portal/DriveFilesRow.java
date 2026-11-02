package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.PrimaryColumn;

/** A referenced Drive file in the reader's list; the row opens the item it is in. */
public record DriveFilesRow(@Hidden String id, @Label("Documento") @PrimaryColumn(caption = "item") String title,
                            @Label("Tipo") String type, @Label("Última modificación") String modified,
                            @Hidden @Label("Contenido") String item) {
}
