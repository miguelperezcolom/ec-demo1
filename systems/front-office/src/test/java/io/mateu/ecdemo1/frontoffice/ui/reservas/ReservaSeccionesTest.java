package io.mateu.ecdemo1.frontoffice.ui.reservas;

import io.mateu.ecdemo1.frontoffice.application.StayQueries;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * «En otros sistemas» e «Historial» se ven en todas las fases: en la llegada, como paneles plegados del
 * foldout (que ocupa la página); en las demás, como secciones de página — nunca dos veces.
 */
class ReservaSeccionesTest {

  final Stay llegando = Stay.fromReservation("QN29HB", "C-1", "JS-SEA", "BRKFST",
      LocalDate.of(2026, 5, 13), LocalDate.of(2026, 5, 14), 2, "Directo · CALLCENTER", new BigDecimal("306.00"), List.of());

  ReservaOverview vista(Stay stay) {
    var queries = new StayQueries(null, null, null, null) {
      @Override
      public Optional<Stay> find(String stayId) {
        return Optional.of(stay);
      }
    };
    var vista = new ReservaOverview(queries, null, null, null, null, null, null, null, null, null, null, null);
    vista.stayId = "QN29HB";
    return vista;
  }

  @Test
  void enLaLlegadaVanEnElFoldoutYNoComoSeccionesDePagina() {
    var vista = vista(llegando);
    assertTrue(vista.isHidden("otrosSistemas", null));
    assertTrue(vista.isHidden("historial", null));
    assertFalse(vista.isHidden("header", null));
    assertFalse(vista.isHidden("cuerpo", null));
  }

  @Test
  void fueraDeLaLlegadaSonSeccionesDePagina() {
    var vista = vista(llegando.cancel());
    assertFalse(vista.isHidden("otrosSistemas", null));
    assertFalse(vista.isHidden("historial", null));
  }

  @Test
  void elModoCheckOutNoEsElFoldoutDeLlegada() {
    var vista = vista(llegando);
    vista.modoCheckout = true;
    assertFalse(vista.isHidden("historial", null));
  }
}
