package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;

public record CustomerSearchRow(@Label("Código") String id,
                                @Label("Nombre") String name,
                                @Label("Email") String email,
                                @Label("Teléfono") String phone,
                                @Label("Documento") String document,
                                @Label("Reservas") int reservations,
                                @Label("Estado") Status status) {
}
