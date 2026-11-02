package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.annotations.PrimaryColumn;
import io.mateu.uidl.annotations.Hidden;

public record RequestRow(@Hidden String id, @Label("Qué") @PrimaryColumn String type, @Label("Fechas") String dates,
                         @Label("Estado") Status status, @Label("Pedida") String created) {
}
