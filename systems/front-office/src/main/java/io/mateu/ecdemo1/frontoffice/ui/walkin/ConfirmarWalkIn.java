package io.mateu.ecdemo1.frontoffice.ui.walkin;

import io.mateu.core.infra.declarative.orchestrators.wizard.WizardStep;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import lombok.Getter;
import lombok.Setter;

/** Paso 4 del walk-in — lo que se va a confirmar, para leerlo antes: la estancia, su precio y el titular. */
@Getter
@Setter
public class ConfirmarWalkIn implements WizardStep {

  @Section(value = "Resumen", propertyList = true)
  @ReadOnly
  @Label("Estancia")
  String resumenEstancia;

  @ReadOnly
  @Label("Huéspedes")
  String resumenHuespedes;

  @ReadOnly
  @Label("Precio del CRS")
  String resumenPrecio;

  @ReadOnly
  @Label("Titular")
  String resumenTitular;

  @ReadOnly
  @Label("Documento")
  String resumenDocumento;
}
