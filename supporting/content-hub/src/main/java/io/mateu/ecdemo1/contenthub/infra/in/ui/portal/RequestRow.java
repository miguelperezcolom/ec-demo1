package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;

public record RequestRow(@Label("Solicitud") String id, @Label("Qué") String type, @Label("Fechas") String dates,
                         @Label("Estado") String status, @Label("Pedida") String created) {
}
