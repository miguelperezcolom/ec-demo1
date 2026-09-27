package io.mateu.ecdemo1.journey.model;

/**
 * The systems a booking goes through, in the order the story is told: each is a lane of the
 * journey. The colour is the lane's, the same in the timeline and in the lanes.
 */
public enum Lane {
    CRS("CRS", "La central de reservas", "#2563eb"),
    CRS_INTEGRATION("Integración CRS", "Recibe el evento y arranca el proceso", "#0891b2"),
    ENGINE("Motor", "EventConductor: el proceso, sus pasos, esperas y candados", "#7c3aed"),
    MAPPING("Mapeado", "Las equivalencias CRS → Opera", "#c026d3"),
    MDM("MDM · clientes", "Quién es el cliente", "#db2777"),
    OPERA("Opera", "El PMS de la cadena", "#ea580c"),
    FRONT_OFFICE("Front office", "La recepción del hotel", "#16a34a"),
    SALESFORCE("Salesforce", "El maestro de clientes", "#0284c7");

    final String label;
    final String description;
    final String color;

    Lane(String label, String description, String color) {
        this.label = label;
        this.description = description;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String color() {
        return color;
    }
}
