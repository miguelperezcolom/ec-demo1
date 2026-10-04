package io.mateu.ecdemo1.frontoffice.ui.reservas;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.VerticalLayout;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A no show is asked for before it is marked: it may cancel the whole reservation in the CRS. */
class NoShowConfirmationTest {

  @Test
  void theDialogSaysWhatItDoesAndConfirmsThatPax() {
    var vista = new ReservaOverview(null, null, null, null, null, null, null, null, null, null, null, null);
    vista.stayId = "QN29HB";

    var dialog = new ReservaDrawers(vista).confirmarNoShow(2, "Lucía Pérez");

    assertThat(dialog.headerTitle()).isEqualTo("¿Marcar no show?");
    var buttons = ((VerticalLayout) dialog.content()).content().stream()
        .filter(Button.class::isInstance).map(Button.class::cast).toList();
    assertThat(buttons).extracting(Button::actionId).containsExactly("confirmarNoShowPax", "cancelarNoShowPax");
    assertThat(String.valueOf(buttons.get(0).parameters())).contains("_item=2");
    assertThat(dialog.toString()).contains("Lucía Pérez no se ha presentado", "el CRS la cancela con su cargo");
  }
}
