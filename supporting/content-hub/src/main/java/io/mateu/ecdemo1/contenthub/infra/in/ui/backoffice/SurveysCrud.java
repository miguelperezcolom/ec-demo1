package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.SurveyRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.Surveys;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/** Every survey, whatever its state. They are closed, never deleted: their answers would go with them. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Encuestas")
@Editors
public class SurveysCrud extends SimpleCrud<SurveyForm, SurveyForm, SurveyForm, NoFilters, SurveysCrud.Row, String> {

    public record Row(@Label("Id") String id, @Label("Título") String title, @Label("Estado") Status status,
                      @Label("Abre") String opens, @Label("Cierra") String closes, @Label("Anónima") String anonymous,
                      @Label("Preguntas") int questions, @Label("Respuestas") long responses) {
    }

    final ObjectProvider<SurveyForm> forms;
    final SurveyRepository repository;
    final Surveys surveys;

    @Override
    protected Class<SurveyForm> formClass() {
        return SurveyForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        return all(repository.findAll().stream().map(s -> new Row(s.id(), s.title(), SurveyForm.status(s),
                Ui.when(s.definition().opensAt()), Ui.when(s.definition().closesAt()), s.anonymous() ? "Sí" : "No",
                s.questions().size(), surveys.responseCount(s.id()))).toList(), "Todavía no hay encuestas");
    }

    @Override
    public SurveyForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(repository.find(id).orElseThrow());
    }

    @Override
    public SurveyForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public SurveyForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        throw new IllegalStateException("Las encuestas no se borran: ciérralas");
    }
}
