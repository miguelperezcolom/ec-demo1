package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.Surveys;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.RowRoute;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/** The surveys open now for the reader's audience, through the visibility function; a row opens it. */
@Service
@RowRoute("/intranet/encuestas/${row.id}")
@Scope("prototype")
@Title("Encuestas")
public class SurveysListing implements Listing<SurveyRow>, Navigable<SurveyPage, String> {

    final PortalSupport portal;
    final Surveys surveys;
    final ObjectProvider<SurveyPage> pages;

    public SurveysListing(PortalSupport portal, Surveys surveys, ObjectProvider<SurveyPage> pages) {
        this.portal = portal;
        this.surveys = surveys;
        this.pages = pages;
    }

    @Override
    public ListingData<SurveyRow> search(SearchRequest request, HttpRequest httpRequest) {
        var rows = surveys.open(portal.reader(httpRequest)).stream().map(o -> new SurveyRow(o.survey().surveyId(),
                o.survey().title(), o.survey().description(),
                o.answered() ? new Status(StatusType.SUCCESS, "Respondida") : new Status(StatusType.WARNING, "Por responder"),
                o.survey().closesAt() == null ? "" : Ui.when(o.survey().closesAt()),
                o.survey().anonymous() ? "Anónima" : "Con tu nombre")).toList();
        return new ListingData<>(new Page<>("", Math.max(20, rows.size()), 0, rows.size(), rows),
                "No hay encuestas abiertas para ti");
    }

    /** Not reached from a row (@RowRoute opens the survey's own route), but what makes it a crud. */
    @Override
    public SurveyPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id);
    }
}
