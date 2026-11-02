package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;

/** A page, in the visual editor's list. */
public record PageRow(@Label("Id") String id, @Label("Página") String title, @Label("Estado") Status status,
                      @Label("Bloques") int blocks, @Label("Modificada") String updatedAt) {
}
