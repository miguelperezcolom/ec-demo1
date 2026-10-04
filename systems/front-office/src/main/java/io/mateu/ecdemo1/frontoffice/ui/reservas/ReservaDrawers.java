package io.mateu.ecdemo1.frontoffice.ui.reservas;

import io.mateu.ecdemo1.frontoffice.domain.stay.IncidentType;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.ui.common.GuestHeaders;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.Dialog;
import io.mateu.uidl.data.Drawer;
import io.mateu.uidl.data.FieldDataType;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.StatusItem;
import io.mateu.uidl.data.StatusList;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.TextContainer;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** The drawers of the in-house toolbar — charges, folio, message, incident, request — and the post-check-in dialog. */
final class ReservaDrawers {

  private final ReservaOverview r;

  ReservaDrawers(ReservaOverview r) {
    this.r = r;
  }

  /** Drawer con el catálogo de cargos: una fila clicable por artículo → postearCargo. */
  Drawer cargos() {
    return Drawer.builder()
        .id("drawer-cargos")
        .headerTitle("Postear cargo")
        .width("30rem")
        .content(StatusList.builder()
            .rowActionId("postearCargo")
            .compact(true)
            .style("width: 100%;")
            .items(r.chargeCatalog.findAll().stream()
                .map(item -> StatusItem.builder()
                    .id(item.code()).title(item.name()).description(item.code())
                    .status(GuestHeaders.euros(item.price())).statusColor("contrast")
                    .build())
                .toList())
            .build())
        .initialData(Map.of("stayId", r.stayId))
        .build();
  }

  /**
   * Drawer con el desglose del folio (Ledger) — gestión de folio: y, cargo a cargo, dónde está en el folio
   * de Opera (el maestro del folio: allí va cada cargo de recepción) y «Anular» para quitarlo — queda
   * anulado aquí y Opera anula su posteo.
   */
  Drawer folio() {
    var folio = r.view().folio();
    var content = new ArrayList<Component>();
    content.add(EstanciaPanel.ledger(folio).style("width: 100%;").build());
    var cargos = folio == null ? List.<io.mateu.ecdemo1.frontoffice.domain.folio.FolioLine>of() : folio.toThePms();
    if (!cargos.isEmpty()) {
      var enOpera = io.mateu.ecdemo1.frontoffice.ui.common.FrontOffice.chargePostings(r.stayId);
      content.add(Text.builder().text("Cargos en el folio de Opera").container(TextContainer.h3).style("margin: 0;").build());
      content.add(StatusList.builder()
          .compact(true)
          .style("width: 100%;")
          .items(cargos.stream().map(l -> {
            var posting = enOpera.get(l.id());
            var estado = posting == null ? "Opera: sin noticias" : posting.state();
            var rechazado = posting != null && posting.refused();
            return StatusItem.builder()
                .id("linea-" + l.id())
                .title(l.concept())
                .description(GuestHeaders.euros(l.amount()) + (l.voided() ? " · anulado" : ""))
                .status(estado)
                .statusColor(rechazado ? "error" : estado.startsWith("Opera: en el folio") || estado.startsWith("Opera: anulado")
                    ? "success" : "warning")
                .actionLabel(l.voided() ? null : "Anular")
                .actionId(l.voided() ? null : "anularCargo")
                .actionIcon(l.voided() ? null : "vaadin:close-circle")
                .build();
          }).toList())
          .build());
    }
    return Drawer.builder()
        .id("drawer-folio")
        .headerTitle("Folio de la estancia")
        .width("34rem")
        .content(VerticalLayout.builder().style("width: 100%; gap: .75rem;").content(content).build())
        .initialData(Map.of("stayId", r.stayId))
        .build();
  }

  /** Drawer de mensaje al huésped: texto libre + Enviar. */
  Drawer mensaje() {
    var guest = r.view().guest();
    return Drawer.builder()
        .id("drawer-mensaje")
        .headerTitle("Mensaje a " + guest.name())
        .width("26rem")
        .content(VerticalLayout.builder()
            .style("gap: .5rem;")
            .content(List.of(
                FormField.builder()
                    .id("mensajeTexto")
                    .label("Mensaje")
                    .dataType(FieldDataType.string)
                    .stereotype(FieldStereotype.textarea)
                    .style("width: 100%;")
                    .build(),
                Button.builder().label("Enviar").actionId("enviarMensaje")
                    .buttonStyle(ButtonStyle.primary).build()))
            .build())
        .initialData(Map.of("stayId", r.stayId))
        .build();
  }

  /** Drawer de alta de incidencia: título/comentario + el TIPO como filas clicables. */
  Drawer nuevaIncidencia() {
    var contenido = new ArrayList<Component>();
    contenido.add(FormField.builder()
        .id("incTitulo").label("Título").dataType(FieldDataType.string)
        .style("width: 100%;").build());
    contenido.add(FormField.builder()
        .id("incComentario").label("Comentario").dataType(FieldDataType.string)
        .style("width: 100%;").build());
    contenido.add(Text.builder().text("Crear como…").container(TextContainer.h3).style("margin: 0;").build());
    contenido.add(StatusList.builder()
        .rowActionId("crearIncidencia")
        .compact(true)
        .style("width: 100%;")
        .items(Arrays.stream(IncidentType.values())
            .map(tipo -> StatusItem.builder().id(tipo.name()).icon(tipo.icon()).title(tipo.label()).build())
            .toList())
        .build());
    return Drawer.builder()
        .id("drawer-nueva-incidencia")
        .headerTitle("Nueva incidencia")
        .width("26rem")
        .content(VerticalLayout.builder().style("gap: .5rem;").content(contenido).build())
        .initialData(Map.of("stayId", r.stayId))
        .build();
  }

  /** Drawer de peticiones del huésped: filas clicables → registrarPeticion. */
  Drawer peticiones() {
    record Peticion(String id, String titulo, String detalle) {}
    var peticiones = List.of(
        new Peticion("late-checkout", "Late check-out", "Salida a las 15:00 · + € 50,00"),
        new Peticion("cuna", "Cuna para la habitación", "Sin cargo"),
        new Peticion("toallas", "Toallas extra", "Sin cargo"),
        new Peticion("limpieza", "Limpieza adicional", "Sin cargo"));
    return Drawer.builder()
        .id("drawer-peticiones")
        .headerTitle("Registrar petición")
        .width("26rem")
        .content(StatusList.builder()
            .rowActionId("registrarPeticion")
            .compact(true)
            .style("width: 100%;")
            .items(peticiones.stream()
                .map(pet -> StatusItem.builder().id(pet.id()).title(pet.titulo()).description(pet.detalle()).build())
                .toList())
            .build())
        .initialData(Map.of("stayId", r.stayId))
        .build();
  }

  /**
   * Antes de marcar un no show: se pregunta, porque no se deshace solo — si nadie más de la reserva ha
   * llegado, es el no show de la reserva, y el CRS la cancela con su cargo (y lo lleva a Opera).
   */
  Dialog confirmarNoShow(int pax, String nombre) {
    return Dialog.builder()
        .id("dialog-no-show")
        .headerTitle("¿Marcar no show?")
        .width("28rem")
        .content(VerticalLayout.builder()
            .style("gap: .25rem;")
            .content(List.of(
                Text.builder().text(nombre + " no se ha presentado.").build(),
                Text.builder().text("Si nadie más de la reserva ha llegado, es el no show de la reserva: el CRS la"
                    + " cancela con su cargo y lo lleva a Opera.").noMargins(true).build(),
                Button.builder()
                    .label("Marcar no show")
                    .actionId("confirmarNoShowPax")
                    .parameters(Map.of("_item", pax))
                    .buttonStyle(ButtonStyle.primary)
                    .build(),
                Button.builder().label("Cancelar").actionId("cancelarNoShowPax").build()))
            .build())
        .initialData(Map.of("stayId", r.stayId))
        .build();
  }

  /** El modal de decisión post-check-in: seguir con la siguiente llegada del grupo o volver al listado. */
  Dialog siguienteDelGrupo(Stay siguiente) {
    var guest = r.queries.view(siguiente.id()).guest();
    return Dialog.builder()
        .id("dialog-siguiente-checkin")
        .headerTitle("Check-in completado")
        .width("28rem")
        .content(VerticalLayout.builder()
            .style("gap: .25rem;")
            .content(List.of(
                Text.builder()
                    .text(guest.name() + " también está por llegar con el grupo "
                        + io.mateu.ecdemo1.frontoffice.application.StayQueries.groupOf(siguiente) + " — "
                        + siguiente.pax() + " pax · " + siguiente.roomType() + " (" + siguiente.agency() + ").")
                    .build(),
                Text.builder().text("¿Seguimos con su check-in?").noMargins(true).build(),
                Button.builder()
                    .label("Check-in de " + guest.name())
                    .actionId("siguienteReserva")
                    .parameters(Map.of("_item", siguiente.id()))
                    .buttonStyle(ButtonStyle.primary)
                    .build(),
                Button.builder().label("Volver al listado").actionId("volverListado").build()))
            .build())
        .build();
  }
}
