package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.SurveyRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.Surveys;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.application.out.SurveyResponseRepository;
import io.mateu.ecdemo1.contenthub.domain.survey.Survey;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyResults;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyStatus;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Publishers;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A survey: its texts and dates, anonymous or named, its audience (the visual builder) and its
 * questions; once it has answers, its results. The toolbar opens and closes it.
 */
@Service
@Scope("prototype")
@Editors
public class SurveyForm extends AudienceForm implements SimpleCrud.CrudForm, VisibilitySupplier {

    @ReadOnly
    @HiddenInCreate
    Status estado;

    @Section("Encuesta")
    @NotEmpty
    @Label("Título")
    String title;
    @ReadOnly
    @HiddenInCreate
    @Label("Id")
    String id;
    @Label("Descripción")
    @Stereotype(FieldStereotype.textarea)
    @Colspan(2)
    String description;
    @Label("Abre el (vacío: al publicarla)")
    LocalDateTime opensAt;
    @Label("Cierra el (vacío: hasta cerrarla a mano)")
    LocalDateTime closesAt;
    @Label("Anónima")
    @Help("No se guarda quién ha respondido qué. No se puede cambiar una vez publicada.")
    boolean anonymous;

    @Section("Preguntas")
    @Label("Preguntas (no se pueden cambiar una vez publicada)")
    List<QuestionForm> questions = new ArrayList<>();

    @Section("Resultados")
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    Callable<Component> resultados = this::results;

    final Surveys surveys;
    final SurveyRepository repository;
    final SurveyResponseRepository responses;
    final AudienceCommands audienceCommands;
    final NamedAudiences namedAudiences;
    final Readers readers;

    public SurveyForm(Surveys surveys, SurveyRepository repository, SurveyResponseRepository responses,
                      AudienceCommands audienceCommands, NamedAudiences namedAudiences, Readers readers) {
        this.surveys = surveys;
        this.repository = repository;
        this.responses = responses;
        this.audienceCommands = audienceCommands;
        this.namedAudiences = namedAudiences;
        this.readers = readers;
    }

    public SurveyForm load(Survey s) {
        var d = s.definition();
        id = s.id();
        title = d.title();
        description = d.description();
        opensAt = Ui.local(d.opensAt());
        closesAt = Ui.local(d.closesAt());
        anonymous = d.anonymous();
        questions = new ArrayList<>(d.questions().stream().map(QuestionForm::of).toList());
        audience(d.audience(), audienceCommands, namedAudiences);
        estado = status(s);
        return this;
    }

    static Status status(Survey s) {
        return new Status(switch (s.status()) {
            case DRAFT -> StatusType.NONE;
            case PUBLISHED -> StatusType.SUCCESS;
            case CLOSED -> StatusType.DANGER;
        }, s.status().label());
    }

    Survey.Definition definition() {
        return new Survey.Definition(title, description, audience(), Ui.instant(opensAt), Ui.instant(closesAt), anonymous,
                questions == null ? List.of() : questions.stream().map(QuestionForm::toQuestion).toList());
    }

    /** On this screen, for editors only (@Editors): a callable section has no request to ask who it is for. */
    Component results() {
        if (id == null) {
            return PortalComponents.muted("");
        }
        var survey = repository.find(id).orElse(null);
        if (survey == null || survey.status() == SurveyStatus.DRAFT) {
            return PortalComponents.muted("Habrá resultados cuando se publique y la respondan.");
        }
        return SurveyResultsView.of(SurveyResults.of(survey, responses.of(id)));
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return surveys.create(readers.of(httpRequest), definition());
    }

    @Override
    public void save(HttpRequest httpRequest) {
        surveys.edit(readers.of(httpRequest), id, definition());
    }

    @Toolbar
    @Publishers
    @Action(confirmationRequired = true, confirmationTitle = "¿Publicar la encuesta?",
            confirmationMessage = "La verá su audiencia desde su fecha de apertura. Después ya no se pueden cambiar sus preguntas ni si es anónima.")
    public Object publicar(HttpRequest httpRequest) {
        surveys.publish(readers.of(httpRequest), id);
        return withNewState(Message.success("Publicada. Su audiencia la verá en «Encuestas» en cuanto abra."));
    }

    @Toolbar
    @Publishers
    @Action(confirmationRequired = true, confirmationTitle = "¿Cerrar la encuesta?",
            confirmationMessage = "Nadie más podrá responder. Los resultados se conservan.")
    public Object cerrar(HttpRequest httpRequest) {
        surveys.close(readers.of(httpRequest), id);
        return withNewState(Message.success("Encuesta cerrada"));
    }

    /** After a button: the message, and the state it left. Only the badge: what has been typed and not saved stays. */
    private Object withNewState(Message message) {
        estado = status(repository.find(id).orElseThrow());
        return List.of(this, message);
    }

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        if (!"publicar".equals(fieldName) && !"cerrar".equals(fieldName)) {
            return false;
        }
        var survey = id == null ? null : repository.find(id).orElse(null);
        if (survey == null) {
            return true;
        }
        return survey.status() != ("publicar".equals(fieldName) ? SurveyStatus.DRAFT : SurveyStatus.PUBLISHED);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nueva encuesta" : title;
    }
}
