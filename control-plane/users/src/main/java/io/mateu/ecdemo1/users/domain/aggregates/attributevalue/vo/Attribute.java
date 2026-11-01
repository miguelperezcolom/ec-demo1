package io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo;

/**
 * The closed list of employee attributes an audience can be written over. Closed on purpose: the
 * content service hashes an employee's attributes to group people who see the same things, and an
 * open list would make every new attribute a silent change to every hash.
 *
 * <p>The label is what the users console shows; the constant's name is what is stored.
 */
public enum Attribute {

    COMPANY("Empresa"),
    COUNTRY("País"),
    REGION("Región"),
    HOTEL("Hotel"),
    DEPARTMENT("Departamento"),
    JOB_TITLE("Puesto"),
    LANGUAGE("Idioma");

    private final String label;

    Attribute(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
