package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.uidl.annotations.Label;

import java.util.Set;

/** Each field narrows the customers; together with the free text, which looks in all of them. */
public class CustomerFilters {

    public enum Estado {
        @Label("Provisional")
        PROVISIONAL,
        @Label("Consolidado")
        CONSOLIDATED
    }

    @Label("Nombre")
    String name;
    @Label("Email")
    String email;
    @Label("Teléfono")
    String phone;
    @Label("Documento")
    String document;
    @Label("Estado")
    Set<Estado> status;
}
