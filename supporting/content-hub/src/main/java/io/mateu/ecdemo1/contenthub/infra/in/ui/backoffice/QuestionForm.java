package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.domain.survey.Question;
import io.mateu.ecdemo1.contenthub.domain.survey.QuestionType;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.VisibilitySupplier;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** One question of a survey as the editor fills it in; the options only for the types that have them. */
@Editors
public class QuestionForm implements OptionsSupplier, VisibilitySupplier {

    @Label("Pregunta")
    public String text;
    @Label("Tipo")
    @Stereotype(FieldStereotype.select)
    public String type = QuestionType.SINGLE_CHOICE.name();
    @Label("Obligatoria")
    public boolean required;
    @HiddenInList
    @Label("Opciones")
    @Help("Una por línea; al menos dos.")
    @Stereotype(FieldStereotype.textarea)
    public String options;
    @HiddenInList
    @Label("Id")
    public String id;

    public static QuestionForm of(Question q) {
        var f = new QuestionForm();
        f.id = q.id();
        f.text = q.text();
        f.type = q.type().name();
        f.required = q.required();
        f.options = String.join("\n", q.options());
        return f;
    }

    public Question toQuestion() {
        return new Question(id == null || id.isBlank() ? "q" + UUID.randomUUID().toString().substring(0, 8) : id, text,
                type == null || type.isBlank() ? null : QuestionType.valueOf(type),
                options == null ? List.of() : Arrays.asList(options.split("\\R")), required);
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "type".equals(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return Arrays.stream(QuestionType.values()).map(t -> new Option(t.name(), t.label())).toList();
    }

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "id" -> true;
            case "options" -> type != null && !type.isBlank() && !QuestionType.valueOf(type).hasOptions();
            default -> false;
        };
    }

    @Override
    public String toString() {
        return text == null ? "Pregunta" : text;
    }
}
