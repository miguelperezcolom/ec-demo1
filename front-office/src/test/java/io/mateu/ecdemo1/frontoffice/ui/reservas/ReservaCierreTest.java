package io.mateu.ecdemo1.frontoffice.ui.reservas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cómo se lee una estancia cerrada: un no show no «salió», y un titular sin documento no es «Doc null». */
class ReservaCierreTest {

  final Stay llegando = Stay.fromReservation("KTQVZJ", "C-1", "STD-KING", "BRKFST",
      LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 30), 2, "Directo · WEB", new BigDecimal("558.00"), List.of());

  @Test
  void unNoShowDiceQueNoLlegoNadieYLoQueCuesta() {
    var noShow = llegando.noShow(new BigDecimal("139.50"));

    assertEquals("no show el 27 sept", ReservaOverview.cierre(noShow));
    assertEquals("No se presentó nadie: el CRS la canceló como no show, con un cargo de 139,50 €",
        ReservaOverview.avisoCierre(noShow));
  }

  @Test
  void unaCanceladaNoSalio() {
    var cancelada = llegando.cancel();

    assertEquals("cancelada", ReservaOverview.cierre(cancelada));
    assertEquals("Reserva cancelada antes de la llegada", ReservaOverview.avisoCierre(cancelada));
  }

  @Test
  void sinDocumentoSoloAdulto() {
    assertEquals("Adulto", ReservaOverview.docAdulto(null));
    assertEquals("Adulto", ReservaOverview.docAdulto(" "));
    assertEquals("Doc 12345678Z · Adulto", ReservaOverview.docAdulto("12345678Z"));
  }
}
