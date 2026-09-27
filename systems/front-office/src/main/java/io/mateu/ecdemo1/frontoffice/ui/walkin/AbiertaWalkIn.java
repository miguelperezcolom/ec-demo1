package io.mateu.ecdemo1.frontoffice.ui.walkin;

import io.mateu.core.infra.declarative.orchestrators.wizard.WizardStep;
import io.mateu.uidl.annotations.ReadOnly;
import lombok.Getter;
import lombok.Setter;

/** El paso de resultado del walk-in. No se ve: confirmar lleva directamente a la estancia abierta. */
@Getter
@Setter
@ReadOnly
public class AbiertaWalkIn implements WizardStep {

  String mensajeWalkIn;
}
