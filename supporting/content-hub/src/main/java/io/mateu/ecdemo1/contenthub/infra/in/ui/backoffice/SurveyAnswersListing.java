package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.SurveyRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.Surveys;
import io.mateu.ecdemo1.contenthub.domain.survey.Answer;
import io.mateu.ecdemo1.contenthub.domain.survey.Survey;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyResponse;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyStatus;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Every answer to every survey, one row each, to search and to export to CSV or Excel. Sorted by
 * survey, question and answer, so that the rows of an anonymous survey cannot be put back together
 * into one person's answers; those rows say neither who nor when.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Respuestas de encuestas")
@Editors
public class SurveyAnswersListing implements Listing<SurveyAnswersListing.Row> {

    public record Row(@Label("Encuesta") String survey, @Label("Nº") int number, @Label("Pregunta") String question,
                      @Label("Respuesta") String answer, @Label("Quién") String who, @Label("Cuándo") String when) {
    }

    final SurveyRepository repository;
    final Surveys surveys;
    final Readers readers;

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var editor = readers.of(httpRequest);
        var text = request == null || request.searchText() == null ? "" : request.searchText().trim().toLowerCase(Locale.ROOT);
        var rows = new ArrayList<Row>();
        for (var survey : repository.findAll()) {
            if (survey.status() != SurveyStatus.DRAFT) {
                surveys.responses(editor, survey.id()).forEach(r -> rows.addAll(rows(survey, r)));
            }
        }
        var shown = rows.stream().filter(r -> text.isEmpty() || r.survey().toLowerCase(Locale.ROOT).contains(text)
                        || r.question().toLowerCase(Locale.ROOT).contains(text)
                        || r.answer().toLowerCase(Locale.ROOT).contains(text))
                .sorted(Comparator.comparing(Row::survey).thenComparing(Row::number).thenComparing(Row::answer))
                .toList();
        return new ListingData<>(new Page<>(text, Math.max(20, shown.size()), 0, shown.size(), shown),
                "Todavía no hay respuestas");
    }

    static List<Row> rows(Survey survey, SurveyResponse response) {
        var out = new ArrayList<Row>();
        var questions = survey.questions();
        for (int i = 0; i < questions.size(); i++) {
            var answer = response.answer(questions.get(i).id());
            if (answer != null && !answer.isEmpty()) {
                out.add(new Row(survey.title(), i + 1, questions.get(i).text(), text(answer),
                        survey.anonymous() ? "Anónima" : response.respondentName() != null ? response.respondentName() : response.respondentId(),
                        survey.anonymous() ? "" : Ui.when(response.submittedAt())));
            }
        }
        return out;
    }

    static String text(Answer a) {
        if (a.rating() != null) {
            return String.valueOf(a.rating());
        }
        return a.text() != null ? a.text() : a.choices().stream().collect(Collectors.joining(", "));
    }

    @Override
    public boolean csvExportable() {
        return true;
    }

    @Override
    public boolean excelExportable() {
        return true;
    }
}
