package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;

/** A person in the intranet's search: the first column opens their card. */
public record PersonRow(@Label("Usuario") String id, @Label("Nombre") String name, @Label("Puesto") String jobTitle,
                        @Label("Departamento") String department, @Label("Hotel") String hotel,
                        @Label("Teléfono") String phone, @Label("Email") String email) {
}
