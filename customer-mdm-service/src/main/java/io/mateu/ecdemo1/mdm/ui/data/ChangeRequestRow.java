package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.uidl.annotations.Details;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;

/**
 * A change a hotel proposed for a customer, and what Salesforce — which decides it — said. The whole
 * proposal is long, so it opens under the row.
 */
public record ChangeRequestRow(String id,
                               @Label("Solicitada") String requestedAt,
                               @Label("Cliente") String customer,
                               @Label("Desde") String origin,
                               @Label("Cambios") String summary,
                               @Label("Estado") Status status,
                               @Label("Decidida") String decidedAt,
                               @Label("Caso en Salesforce") String salesforceCase,
                               @Details String detail) {
}
