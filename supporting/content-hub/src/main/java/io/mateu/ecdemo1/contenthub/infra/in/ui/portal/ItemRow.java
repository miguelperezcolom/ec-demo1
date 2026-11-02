package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.PrimaryColumn;
import io.mateu.uidl.annotations.Hidden;

/** A visible item in a listing. */
public record ItemRow(@Hidden String id, @Label("Título") @PrimaryColumn(caption = "summary") String title, @Label("Tipo") String type,
                      @Label("Sección") String section, @Label("Fecha") String date, @Hidden @Label("Resumen") String summary) {
}
