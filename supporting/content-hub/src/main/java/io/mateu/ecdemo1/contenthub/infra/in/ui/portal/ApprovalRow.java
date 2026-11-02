package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.PrimaryColumn;
import io.mateu.uidl.annotations.Hidden;

public record ApprovalRow(@Hidden String id, @Label("Quién") @PrimaryColumn(caption = "type") String employee,
                          @Hidden @Label("Qué") String type,
                          @Label("Fechas") String dates, @Label("Te toca como") String role, @Label("Vence") String due) {
}
