package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.PrimaryColumn;
import io.mateu.uidl.data.Status;

/** An open survey in the reader's list: the first column opens it. */
public record SurveyRow(@Hidden String id, @Label("Encuesta") @PrimaryColumn(caption = "description") String title,
                        @Hidden @Label("Descripción") String description, @Label("Estado") Status state,
                        @Label("Cierra") String closes, @Label("Respuesta") String kind) {
}
