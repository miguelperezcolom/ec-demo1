package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;

public record ItemRow(@Label("Id") String id, @Label("Título") String title, @Label("Tipo") String type,
                      @Label("Estado") Status status, @Label("Autor") String author, @Label("Modificado") String updatedAt) {
}
