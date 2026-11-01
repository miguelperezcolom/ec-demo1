package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;

/** A visible item in a listing. */
public record ItemRow(@Label("Id") String id, @Label("Título") String title, @Label("Tipo") String type,
                      @Label("Sección") String section, @Label("Fecha") String date, @Label("Resumen") String summary) {
}
