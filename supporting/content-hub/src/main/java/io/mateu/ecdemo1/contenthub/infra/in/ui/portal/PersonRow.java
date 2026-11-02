package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.PrimaryColumn;
import io.mateu.uidl.annotations.Hidden;

/** A person in the intranet's search: the first column opens their card. */
public record PersonRow(@Hidden String id, @Label("Nombre") @PrimaryColumn(caption = "jobTitle") String name,
                        @Hidden @Label("Puesto") String jobTitle,
                        @Label("Departamento") String department, @Label("Hotel") String hotel,
                        @Label("Teléfono") String phone, @Label("Email") String email) {
}
