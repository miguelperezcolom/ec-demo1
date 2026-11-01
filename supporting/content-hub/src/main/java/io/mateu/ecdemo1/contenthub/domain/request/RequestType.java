package io.mateu.ecdemo1.contenthub.domain.request;

/** What an employee can ask for from the intranet. */
public enum RequestType {
    VACACIONES("Vacaciones"),
    FORMACION("Formación"),
    MATERIAL("Material");

    private final String label;

    RequestType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
