package io.mateu.ecdemo1.frontoffice.ui.reservas;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.VerticalLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
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

  /**
   * Mateu (392 on) refuses an action its component does not declare in {@code supportsAction}: the
   * desk got «Tu sesión ya no es válida» on No show, whose three actions were handled and not declared.
   */
  @Test
  void everyActionTheViewHandlesIsDeclared() throws Exception {
    var vista = new ReservaOverview(null, null, null, null, null, null, null, null, null, null, null, null);
    var source = Files.readString(Path.of("src/main/java/io/mateu/ecdemo1/frontoffice/ui/reservas/ReservaOverview.java"));
    var handle = source.substring(source.indexOf("public Object handleAction("));
    handle = handle.substring(0, handle.indexOf("\n  }\n"));
    var cases = Pattern.compile("case ((?:\"\\w+\"(?:, )?)+) ->").matcher(handle);
    var handled = new java.util.TreeSet<String>();
    while (cases.find()) {
      var ids = Pattern.compile("\"(\\w+)\"").matcher(cases.group(1));
      while (ids.find()) {
        handled.add(ids.group(1));
      }
    }
    assertThat(handled).contains("noShowPax", "confirmarNoShowPax", "cancelarNoShowPax", "guardarExtras");
    assertThat(handled).allSatisfy(id -> assertThat(vista.supportsAction(id)).as(id).isTrue());
  }
}
