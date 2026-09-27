package io.mateu.ecdemo1.frontoffice.ui.walkin;

import io.mateu.core.infra.declarative.orchestrators.wizard.WizardStep;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.StereotypeSupplier;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Paso 3 del walk-in — el titular de la reserva, la persona que está en el mostrador. */
@Getter
@Setter
public class TitularWalkIn implements WizardStep, OptionsSupplier, StereotypeSupplier {

  @Section(value = "Titular", columns = 2)
  @Label("Nombre")
  String nombre;

  @Label("Apellidos")
  String apellidos;

  @Label("Email")
  String email;

  @Label("Teléfono")
  String telefono;

  @Label("Nacionalidad")
  @Help("Código ISO de dos letras: ES, DE, GB…")
  String nacionalidad = "ES";

  @Label("Tipo de documento")
  String tipoDocumento = "PASSPORT";

  @Label("Documento")
  String documento;

  @Override
  public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
    return TitularWalkIn.class.equals(formType) && "tipoDocumento".equals(fieldName);
  }

  @Override
  public List<Option> options(String fieldName, HttpRequest httpRequest) {
    return "tipoDocumento".equals(fieldName)
        ? List.of(new Option("PASSPORT", "Pasaporte"), new Option("ID_CARD", "DNI / documento de identidad"),
            new Option("DRIVING_LICENSE", "Permiso de conducir"))
        : List.of();
  }

  @Override
  public FieldStereotype stereotype(String memberName, HttpRequest httpRequest) {
    return "tipoDocumento".equals(memberName) ? FieldStereotype.select : null;
  }
}
