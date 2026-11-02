package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.core.infra.declarative.orchestrators.dashboard.Dashboard;
import io.mateu.ecdemo1.contenthub.application.out.UsageRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.UsageAnalytics;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.uidl.annotations.Panel;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.Chart;
import io.mateu.uidl.data.ChartData;
import io.mateu.uidl.data.ChartDataset;
import io.mateu.uidl.data.ChartOptions;
import io.mateu.uidl.data.ChartType;
import io.mateu.uidl.data.EmptyState;
import io.mateu.uidl.data.MetricCard;
import io.mateu.uidl.fluent.Component;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The intranet's usage, for the editors: Mateu's dashboard archetype — consecutive cards are a band, each
 * panel a tile. Last 30 days; the figures per day come from the daily aggregates, up to 15 minutes old.
 * Everything is worked out when the page is built: nothing here keeps state.
 */
@Service
@Scope("prototype")
@Title("Analítica")
@Editors
public class AnalyticsPage extends Dashboard {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    final MetricCard activos7;
    final MetricCard activos30;
    final MetricCard vistas;
    final MetricCard busquedas;
    final MetricCard sinResultados;

    @Panel(title = "Vistas por día", subtitle = "Últimos 30 días", colSpan = 2)
    final Component vistasPorDia;

    @Panel(title = "Contenidos más vistos", colSpan = 2)
    final Component contenidos;

    @Panel(title = "Lo más buscado")
    final Component masBuscado;

    @Panel(title = "Búsquedas sin resultados", subtitle = "Lo que la gente busca y la intranet no tiene")
    final Component sinResultado;

    @Panel(title = "Adopción por hotel", subtitle = "% de empleados que han leído algo en 30 días")
    final Component porHotel;

    @Panel(title = "Adopción por departamento", subtitle = "% de empleados que han leído algo en 30 días")
    final Component porDepartamento;

    @Panel(title = "Newsletter", subtitle = "Aperturas y clics de las últimas ediciones", colSpan = 2)
    final Component newsletter;

    public AnalyticsPage(UsageAnalytics analytics) {
        var o = analytics.overview();
        activos7 = card("activos7", "Lectores activos (7 días)", o.active7(), "de " + o.employees() + " empleados");
        activos30 = card("activos30", "Lectores activos (30 días)", o.active30(), "de " + o.employees() + " empleados");
        vistas = card("vistas", "Vistas (30 días)", o.views(), null);
        busquedas = card("busquedas", "Búsquedas (30 días)", o.searches(), null);
        sinResultados = MetricCard.builder().id("sinResultados").title("Búsquedas sin resultados")
                .value(String.format(Locale.of("es"), "%.1f", o.emptyPercent())).unit("%").build();
        vistasPorDia = o.views() == 0 ? nothing("Nadie ha abierto todavía ningún contenido")
                : chart(ChartType.line, o.viewsPerDay().keySet().stream().map(DAY::format).toList(),
                new ChartDataset("Vistas", o.viewsPerDay().values().stream().map(Long::doubleValue).toList()));
        contenidos = ranked(o.topItems(), "Vistas", "Nadie ha abierto todavía ningún contenido");
        masBuscado = ranked(o.topSearches(), "Búsquedas", "Nadie ha buscado nada todavía");
        sinResultado = ranked(o.emptySearches(), "Búsquedas", "Toda búsqueda ha encontrado algo");
        porHotel = adoption(o.byHotel());
        porDepartamento = adoption(o.byDepartment());
        newsletter = o.newsletters().isEmpty() ? nothing("Ninguna edición abierta todavía")
                : chart(ChartType.bar, o.newsletters().stream().map(e -> e.title()).toList(),
                new ChartDataset("Aperturas", o.newsletters().stream().map(e -> (double) e.opens()).toList()),
                new ChartDataset("Clics", o.newsletters().stream().map(e -> (double) e.clicks()).toList()));
    }

    @Override
    protected int columns() {
        return 2;
    }

    static MetricCard card(String id, String title, long value, String description) {
        return MetricCard.builder().id(id).title(title).value(String.valueOf(value)).description(description).build();
    }

    static Component ranked(List<UsageRepository.Ranked> rows, String label, String empty) {
        return rows.isEmpty() ? nothing(empty) : chart(ChartType.bar, rows.stream().map(UsageRepository.Ranked::key).toList(),
                new ChartDataset(label, rows.stream().map(r -> (double) r.value()).toList()));
    }

    static Component adoption(List<UsageAnalytics.Adoption> rows) {
        return rows.isEmpty() ? nothing("El directorio aún está vacío") : chart(ChartType.bar,
                rows.stream().map(UsageAnalytics.Adoption::label).toList(),
                new ChartDataset("% activos", rows.stream().map(a -> Math.round(a.percent() * 10) / 10d).toList()));
    }

    static Component chart(ChartType type, List<String> labels, ChartDataset... datasets) {
        return Chart.builder().chartType(type)
                .chartData(ChartData.builder().labels(labels).datasets(List.of(datasets)).build())
                // no aspect ratio: the style sets the height and the chart fits without scrolling
                .chartOptions(ChartOptions.builder().maintainAspectRatio(false).build())
                .style("width: 100%; height: 280px;")
                .build();
    }

    static Component nothing(String text) {
        return EmptyState.builder().title(text).build();
    }

    @Override
    public String toString() {
        return "Analítica";
    }
}
