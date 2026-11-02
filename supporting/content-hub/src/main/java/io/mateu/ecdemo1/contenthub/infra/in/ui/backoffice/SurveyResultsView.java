package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.domain.survey.QuestionType;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyResults;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents;
import io.mateu.uidl.data.Chart;
import io.mateu.uidl.data.ChartData;
import io.mateu.uidl.data.ChartDataset;
import io.mateu.uidl.data.ChartOptions;
import io.mateu.uidl.data.ChartType;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.fluent.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A survey's results with Mateu's components, one section per question: a pie for a single choice
 * (its parts add up to all), bars for a multiple choice and a rating, with the counts and percentages
 * under them; the free-text answers as they were written. Nothing here says who answered.
 */
final class SurveyResultsView {

    private SurveyResultsView() {
    }

    static Component of(SurveyResults results) {
        var parts = new ArrayList<Component>();
        parts.add(Text.builder().text(results.responses() == 1 ? "1 respuesta" : results.responses() + " respuestas").build());
        results.questions().forEach(q -> parts.add(PageRenderer.section(q.question().text(), question(q))));
        return PortalComponents.column(parts);
    }

    static Component question(SurveyResults.QuestionResult q) {
        if (q.answered() == 0) {
            return PortalComponents.muted("Nadie la ha respondido todavía.");
        }
        var parts = new ArrayList<Component>();
        if (q.question().type() == QuestionType.FREE_TEXT) {
            q.texts().forEach(t -> parts.add(Text.builder().text(t).build()));
            return PortalComponents.column(parts);
        }
        parts.add(chart(q));
        q.counts().forEach(c -> parts.add(PortalComponents.small(c.label() + ": " + c.count() + " ("
                + String.format(Locale.of("es"), "%.1f", c.percent()) + " %)")));
        if (q.average() != null) {
            parts.add(Text.builder().text("Media: " + String.format(Locale.of("es"), "%.1f", q.average())).build());
        }
        return PortalComponents.column(parts);
    }

    static Chart chart(SurveyResults.QuestionResult q) {
        List<Double> data = q.counts().stream().map(c -> (double) c.count()).toList();
        return Chart.builder()
                .chartType(q.question().type() == QuestionType.SINGLE_CHOICE ? ChartType.pie : ChartType.bar)
                .chartData(ChartData.builder().labels(q.counts().stream().map(SurveyResults.Count::label).toList())
                        .datasets(List.of(ChartDataset.builder().label("Respuestas").data(data).build())).build())
                // no aspect ratio: the style sets the height
                .chartOptions(ChartOptions.builder().maintainAspectRatio(false).build())
                .style("width: 100%; height: 240px;")
                .build();
    }
}
