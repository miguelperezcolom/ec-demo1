package io.mateu.ecdemo1.contenthub.domain.survey;

public enum SurveyStatus {
    DRAFT("Borrador"), PUBLISHED("Publicada"), CLOSED("Cerrada");

    private final String label;

    SurveyStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
