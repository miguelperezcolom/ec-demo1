package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.Surveys;
import io.mateu.ecdemo1.contenthub.domain.survey.Question;
import io.mateu.ecdemo1.contenthub.domain.survey.Survey;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.EmptyState;
import io.mateu.uidl.data.FieldDataType;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.FormLayout;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.MultimediaContentPage;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.fluent.UserTrigger;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer.section;

/**
 * A survey for a reader to answer — only reachable while it is open and for their audience — on the
 * same page template as an item: a banner with its title and description and «Enviar» as its call to
 * action, and under it a field per question. Answered once; afterwards the page just says so.
 */
@Service
@Scope("prototype")
public class SurveyPage implements ComponentTreeSupplier, Identifiable {

    String id;

    final PortalSupport portal;
    final Surveys surveys;

    public SurveyPage(PortalSupport portal, Surveys surveys) {
        this.portal = portal;
        this.surveys = surveys;
    }

    public SurveyPage load(String id) {
        this.id = id;
        return this;
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        if (id == null) {
            id = httpRequest.lastPathItem();
        }
        var reader = portal.reader(httpRequest);
        var survey = surveys.forReader(reader, id).orElse(null);
        if (survey == null) {
            return Form.builder().title("No disponible")
                    .content(List.of(EmptyState.builder().title("Esta encuesta no existe, no está abierta o no es para ti")
                            .description("Puede que ya haya cerrado o que no esté dirigida a tu perfil.").build()))
                    .build();
        }
        var content = new ArrayList<Component>(PortalComponents.viewingAs(reader.viewingAs()));
        var toolbar = new ArrayList<UserTrigger>();
        if (surveys.answered(reader, survey)) {
            content.add(Notice.builder().theme("success").text("Ya has respondido a esta encuesta. ¡Gracias!").build());
        } else {
            content.add(Notice.builder().text(survey.anonymous()
                    ? "Es anónima: se anota que has respondido, para que no se responda dos veces, pero tus respuestas no llevan nada que te identifique."
                    : "No es anónima: los responsables verán tu nombre junto a tus respuestas.").build());
            // a FormLayout: Redwood draws a radio or a checkbox set only there, and a loose field as a text box
            content.add(section(null, FormLayout.builder().maxColumns(1)
                    .content(survey.questions().stream().map(SurveyPage::field).toList()).build()));
            if (reader.viewingAs() == null) {
                toolbar.add(Button.builder().label("Enviar").actionId("enviar").buttonStyle(ButtonStyle.primary).build());
            }
        }
        return Form.builder()
                .title(survey.title())
                .overline(about(survey))
                .subtitle(survey.definition().description())
                .template(new MultimediaContentPage(PortalComponents.TONE.name()))
                .toolbar(toolbar)
                .content(content)
                .build();
    }

    static String about(Survey survey) {
        var closes = survey.definition().closesAt();
        return "Encuesta" + (closes == null ? "" : " · hasta el " + Ui.when(closes));
    }

    /** A rating is five radio buttons rather than stars: stars are not drawn by both renderers. */
    static Component field(Question q) {
        var field = FormField.builder().id(SurveyAnswers.field(q)).label(q.text()).required(q.required());
        return switch (q.type()) {
            case SINGLE_CHOICE -> field.dataType(FieldDataType.string).stereotype(FieldStereotype.radio)
                    .options(q.options().stream().map(o -> new Option(o, o)).toList()).build();
            case MULTIPLE_CHOICE -> field.dataType(FieldDataType.array).stereotype(FieldStereotype.checkbox)
                    .options(q.options().stream().map(o -> new Option(o, o)).toList()).build();
            case RATING -> field.dataType(FieldDataType.string).stereotype(FieldStereotype.radio)
                    .options(IntStream.rangeClosed(1, 5).mapToObj(String::valueOf).map(v -> new Option(v, v)).toList())
                    .description("De 1 (poco) a 5 (mucho)").build();
            case FREE_TEXT -> field.dataType(FieldDataType.string).stereotype(FieldStereotype.textarea).build();
        };
    }

    @Action
    public Object enviar(HttpRequest httpRequest) {
        Reader reader = portal.reader(httpRequest);
        var survey = surveys.forReader(reader, id).orElse(null);
        if (survey == null) {
            return Message.error("Esta encuesta ya no está abierta");
        }
        var state = httpRequest.runActionRq() == null ? null : httpRequest.runActionRq().componentState();
        // a refusal (a required question empty, already answered) reaches the reader as a message: UserFacingErrors
        surveys.answer(reader, id, SurveyAnswers.from(survey, state));
        return List.of(this, Message.success("Respuesta enviada. ¡Gracias!"));
    }

    /** Surveys as a list for a tile of the home: the title opens it. */
    static Component links(List<ContentIndex.VisibleSurvey> list) {
        return PortalComponents.column(list.stream().<Component>map(s -> VerticalLayout.builder().spacing(false)
                .padding(false).content(List.of(new Anchor(s.title(), Ui.PORTAL + "/encuestas/" + s.surveyId()),
                        PortalComponents.small(s.closesAt() == null ? "Sin fecha de cierre"
                                : "Hasta el " + Ui.when(s.closesAt())))).build()).toList());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Encuesta";
    }
}
