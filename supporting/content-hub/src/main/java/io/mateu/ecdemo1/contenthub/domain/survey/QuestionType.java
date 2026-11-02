package io.mateu.ecdemo1.contenthub.domain.survey;

public enum QuestionType {
    SINGLE_CHOICE("Opción única"), MULTIPLE_CHOICE("Opción múltiple"), RATING("Valoración de 1 a 5"),
    FREE_TEXT("Texto libre");

    private final String label;

    QuestionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean hasOptions() {
        return this == SINGLE_CHOICE || this == MULTIPLE_CHOICE;
    }
}
