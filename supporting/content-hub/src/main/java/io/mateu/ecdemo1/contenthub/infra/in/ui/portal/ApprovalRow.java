package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;

public record ApprovalRow(@Label("Tarea") String id, @Label("Quién") String employee, @Label("Qué") String type,
                          @Label("Fechas") String dates, @Label("Te toca como") String role, @Label("Vence") String due) {
}
