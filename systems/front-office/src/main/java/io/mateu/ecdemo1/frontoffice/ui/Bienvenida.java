package io.mateu.ecdemo1.frontoffice.ui;

import io.mateu.core.infra.declarative.orchestrators.welcome.Welcome;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Panel;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonStyle;
import java.net.URI;

/**
 * La landing de la suite con el arquetipo {@link Welcome} (template RDS de welcome page): hero
 * con las llamadas a la acción y las tres áreas de la suite como tiles. Es la HOME del app
 * (ruta declarada en {@code specs/ui/routes.yaml}).
 */
@Title("Front-Office Suite")
public class Bienvenida extends Welcome {

  Button reservas =
      Button.builder()
          .label("Ir a Reservas")
          .actionId("abrirReservas")
          .buttonStyle(ButtonStyle.primary)
          .build();

  Button automatizaciones =
      Button.builder().label("Automatizaciones").actionId("abrirAutomatizaciones").build();

  // los 3 KPIs del día, con los contadores VIVOS del repositorio (la vista se instancia
  // por request, así que los initializers cuentan en cada render)
  @Panel(title = "")
  io.mateu.uidl.data.MetricCard llegadasHoy =
      io.mateu.uidl.data.MetricCard.builder()
          .id("llegadas-hoy")
          .actionId("verLlegadasHoy")
          .title("LLEGADAS HOY")
          .value(String.valueOf(contar(Vista.LLEGADAS)))
          .description("Reservas que entran hoy")
          .build();

  @Panel(title = "")
  io.mateu.uidl.data.MetricCard enCasa =
      io.mateu.uidl.data.MetricCard.builder()
          .id("en-casa")
          .actionId("verEnCasa")
          .title("IN HOUSE")
          .value(String.valueOf(contar(Vista.EN_CASA)))
          .description("Huéspedes en casa")
          .build();

  @Panel(title = "")
  io.mateu.uidl.data.MetricCard salidasHoy =
      io.mateu.uidl.data.MetricCard.builder()
          .id("salidas-hoy")
          .actionId("verSalidasHoy")
          .title("SALIDAS HOY")
          .value(String.valueOf(contar(Vista.SALIDAS)))
          .description("Check-outs previstos hoy")
          .build();

  // previsión de ocupación de los próximos 7 días, a todo el ancho bajo los KPIs —
  // un Chart de BARRAS con altura contenida (cabe sin scroll en el renderer web)
  @Panel(title = "Ocupación próximos 7 días", colSpan = 3)
  io.mateu.uidl.data.Chart ocupacion = ocupacionProximosDias();

  private static io.mateu.uidl.data.Chart ocupacionProximosDias() {
    var today = java.time.LocalDate.now();
    var reads = io.mateu.ecdemo1.frontoffice.ui.common.FrontOffice.stayReads();
    var rooms = Math.max(1, reads.rooms());
    var dias = java.time.format.DateTimeFormatter.ofPattern(
        "EEE d", java.util.Locale.forLanguageTag("es"));
    var labels = new java.util.ArrayList<String>();
    var values = new java.util.ArrayList<Double>();
    // habitaciones ocupadas cada noche: una consulta para los 7 días, no un findAll por día
    var noches = reads.occupiedNights(today, 7);
    for (int i = 0; i < 7; i++) {
      var day = today.plusDays(i);
      var ocupadas = noches.get(i);
      labels.add(dias.format(day));
      values.add(Math.min(100d, Math.round(ocupadas * 1000d / rooms) / 10d));
    }
    return io.mateu.uidl.data.Chart.builder()
        .chartType(io.mateu.uidl.data.ChartType.bar)
        .chartData(
            io.mateu.uidl.data.ChartData.builder()
                .labels(labels)
                .datasets(
                    java.util.List.of(
                        io.mateu.uidl.data.ChartDataset.builder()
                            .label("Ocupación %")
                            .data(values)
                            .build()))
                .build())
        // sin aspect ratio: la altura la fija el style y el chart cabe sin scroll
        .chartOptions(
            io.mateu.uidl.data.ChartOptions.builder().maintainAspectRatio(false).build())
        .style("width: 100%; height: 200px;")
        .build();
  }

  private enum Vista { LLEGADAS, SALIDAS, EN_CASA }

  /** Un contador del día: una consulta de agregados, no un findAll de agregados completos. */
  private static long contar(Vista vista) {
    var hoy = io.mateu.ecdemo1.frontoffice.ui.common.FrontOffice.stayReads()
        .today(java.time.LocalDate.now());
    return switch (vista) {
      case LLEGADAS -> hoy.arrivals();
      case SALIDAS -> hoy.departures();
      case EN_CASA -> hoy.inHouse();
    };
  }

  @Override
  protected String heroTitle() {
    return "Front-Office Suite";
  }

  @Override
  protected String heroSubtitle() {
    return "Recepción y automatización del hotel — llegadas, estancias y salidas en una sola vista";
  }

  @Action
  Object abrirReservas() {
    return URI.create("/reservas");
  }

  // cada KPI abre el listado con SU vista aplicada — el filtro viaja en la URL
  // (?vista=...) y el renderer lo aplica como selector rápido al aterrizar
  @Action
  Object verLlegadasHoy() {
    return URI.create("/reservas?vista=LLEGADAS_HOY");
  }

  @Action
  Object verEnCasa() {
    return URI.create("/reservas?vista=IN_HOUSE");
  }

  @Action
  Object verSalidasHoy() {
    return URI.create("/reservas?vista=SALIDAS_HOY");
  }

  @Action
  Object abrirAutomatizaciones() {
    return URI.create("/automatizaciones");
  }
}
