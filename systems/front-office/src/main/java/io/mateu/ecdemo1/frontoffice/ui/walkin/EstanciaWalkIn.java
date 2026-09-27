package io.mateu.ecdemo1.frontoffice.ui.walkin;

import io.mateu.core.infra.declarative.orchestrators.wizard.WizardStep;
import io.mateu.ecdemo1.frontoffice.application.WalkInService;
import io.mateu.ecdemo1.frontoffice.infra.crs.WalkInDesk;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.StereotypeSupplier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import io.mateu.uidl.di.MateuBeanProvider;

/**
 * Paso 1 del walk-in — la estancia: fechas, lo que el CRS vende (habitación, tarifa, régimen, en
 * desplegables con sus códigos y nombres) y quién duerme.
 */
@Getter
@Setter
public class EstanciaWalkIn implements WizardStep, OptionsSupplier, StereotypeSupplier {

  static final List<String> SELECTS = List.of("habitacion", "tarifa", "regimen");

  @Section(value = "Estancia", columns = 2)
  @Label("Llegada")
  LocalDate llegada = LocalDate.now();

  @Label("Salida")
  LocalDate salida = LocalDate.now().plusDays(1);

  @Label("Habitación")
  String habitacion;

  @Label("Tarifa")
  String tarifa;

  @Label("Régimen")
  String regimen;

  @Label("Adultos")
  int adultos = 2;

  @Label("Edades de los niños")
  @Help("Separadas por comas, p. ej. 4, 9. Vacío si no vienen niños.")
  String edadesNinos;

  /** Lo que falta o no cuadra para pedir precio; null si nada. */
  String problema() {
    var falta = new ArrayList<String>();
    if (llegada == null) falta.add("llegada");
    if (salida == null) falta.add("salida");
    if (blank(habitacion)) falta.add("habitación");
    if (blank(tarifa)) falta.add("tarifa");
    if (blank(regimen)) falta.add("régimen");
    if (!falta.isEmpty()) {
      return "Falta de la estancia: " + String.join(", ", falta) + ".";
    }
    if (!salida.isAfter(llegada)) {
      return "La salida tiene que ser después de la llegada.";
    }
    if (adultos < 1) {
      return "Tiene que venir al menos un adulto.";
    }
    try {
      edades();
    } catch (IllegalArgumentException e) {
      return e.getMessage();
    }
    return null;
  }

  List<Integer> edades() {
    if (blank(edadesNinos)) {
      return List.of();
    }
    var edades = new ArrayList<Integer>();
    for (var parte : edadesNinos.split("[,;\\s]+")) {
      if (!parte.isBlank()) {
        try {
          edades.add(Integer.parseInt(parte.trim()));
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Edad de niño no válida: «" + parte.trim() + "»");
        }
      }
    }
    return edades;
  }

  /** Lo que decide el precio: si cambia tras pedirlo, el precio ya no vale. */
  String huella() {
    return llegada + "|" + salida + "|" + habitacion + "|" + tarifa + "|" + regimen + "|" + adultos + "|"
        + edadesNinos;
  }

  @Override
  public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
    return EstanciaWalkIn.class.equals(formType) && SELECTS.contains(fieldName);
  }

  /** Los códigos del CRS para este hotel, con sus nombres: es el CRS quien los vende. */
  @Override
  public List<Option> options(String fieldName, HttpRequest httpRequest) {
    WalkInDesk.Offer offer;
    try {
      // Mateu re-creates the steps from the state with new — nothing injected — so the catalog is
      // asked of Mateu's bean provider
      offer = MateuBeanProvider.getBean(WalkInService.class).offer();
    } catch (RuntimeException e) {
      return List.of(); // sin catálogo del CRS el paso se abre igual; pedir precio dirá qué falla
    }
    var options = switch (fieldName) {
      case "habitacion" -> offer.roomTypes();
      case "tarifa" -> offer.ratePlans();
      case "regimen" -> offer.boards();
      default -> List.<WalkInDesk.Option>of();
    };
    return options.stream().map(o -> new Option(o.code(), o.name() + " (" + o.code() + ")")).toList();
  }

  @Override
  public FieldStereotype stereotype(String memberName, HttpRequest httpRequest) {
    return SELECTS.contains(memberName) ? FieldStereotype.select : null;
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
