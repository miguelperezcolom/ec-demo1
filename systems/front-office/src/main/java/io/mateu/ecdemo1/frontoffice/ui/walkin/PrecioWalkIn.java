package io.mateu.ecdemo1.frontoffice.ui.walkin;

import io.mateu.core.infra.declarative.orchestrators.wizard.WizardStep;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.fluent.Component;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import lombok.Getter;
import lombok.Setter;

/**
 * Paso 2 del walk-in — el precio del CRS, pedido al entrar en el paso: las noches, el precio por
 * noche y el total. Si el CRS no la vende así, lo dice, y «Back» vuelve a la estancia para cambiarla.
 */
@Getter
@Setter
public class PrecioWalkIn implements WizardStep {

  /** Lo que el CRS dijo, y de qué estancia: si la estancia cambia, hay que volver a preguntar. */
  @Hidden BigDecimal presupuesto;

  @Hidden String presupuestoDe;

  @Hidden String errorCrs;

  @Section(value = "", frameless = true)
  @Label("")
  Callable<Component> avisoCrs = () -> errorCrs == null || errorCrs.isBlank()
      ? Notice.builder().theme("info").slim(true).fullWidth(true)
          .text("Precio del CRS para esta estancia: es el que se le cobra al huésped.").build()
      : Notice.builder().theme("danger").fullWidth(true)
          .text("El CRS no da precio: " + errorCrs + " Vuelve atrás («Back») y cambia la estancia.").build();

  @Section(value = "Precio del CRS", propertyList = true)
  @ReadOnly
  @Label("Noches")
  String noches = "—";

  @ReadOnly
  @Label("Precio por noche")
  String precioNoche = "—";

  @ReadOnly
  @Label("Total")
  String totalCrs = "—";

  boolean cotizado() {
    return presupuesto != null && (errorCrs == null || errorCrs.isBlank());
  }
}
