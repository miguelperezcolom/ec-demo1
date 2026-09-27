package io.mateu.ecdemo1.frontoffice.ui.walkin;

import io.mateu.ecdemo1.frontoffice.infra.crs.WalkInDesk;
import io.mateu.ecdemo1.frontoffice.ui.common.GuestHeaders;
import io.mateu.uidl.annotations.Button;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.StereotypeSupplier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Un cliente sin reserva en recepción. El precio lo pone el CRS, que es quien vende: «Calcular
 * precio» se lo pregunta; «Confirmar» abre la estancia aquí mismo — para hacer el check-in ya — y
 * pide al CRS la reserva a ese precio, que baja después a Opera y vuelve a esta estancia.
 */
@Getter
@Setter
@Title("Walk-in")
public class WalkInForm implements OptionsSupplier, StereotypeSupplier {

  static final List<String> SELECTS = List.of("habitacion", "tarifa", "regimen", "tipoDocumento");

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

  @Section(value = "Precio del CRS", columns = 1)
  @ReadOnly
  @Label("Precio")
  String precio = "Pulsa «Calcular precio»: lo pone el CRS.";

  /** Lo que el CRS dijo, y de qué: si algo cambia después, hay que volver a preguntar. */
  @Hidden BigDecimal presupuesto;

  @Hidden String presupuestoDe;

  @Button(order = 1)
  @Label("Calcular precio")
  public Object calcularPrecio() {
    try {
      var quote = WalkInDesk.desk().quote(request(null));
      presupuesto = quote.total();
      presupuestoDe = huella();
      precio = quote.nights() + (quote.nights() == 1 ? " noche" : " noches") + " · "
          + GuestHeaders.euros(quote.total()).replace("€ ", "") + " " + quote.currency();
      return new State(this);
    } catch (RuntimeException e) {
      presupuesto = null;
      presupuestoDe = null;
      precio = e.getMessage();
      return List.of(Message.error(e.getMessage()), new State(this));
    }
  }

  @Button(order = 2)
  @Label("Confirmar walk-in")
  public Object confirmar() {
    if (presupuesto == null || !Objects.equals(presupuestoDe, huella())) {
      return Message.warning("Calcula el precio antes de confirmar: el CRS tiene que decir cuánto cuesta esto que se confirma.");
    }
    var falta = new ArrayList<String>();
    if (blank(nombre)) falta.add("nombre");
    if (blank(apellidos)) falta.add("apellidos");
    if (blank(documento)) falta.add("documento");
    if (!falta.isEmpty()) {
      return Message.warning("Falta del titular: " + String.join(", ", falta) + ".");
    }
    var desk = WalkInDesk.desk();
    var quote = new WalkInDesk.Quote(null, 0, presupuesto);
    var walkIn = desk.send(desk.open(request(presupuesto), quote));
    var estado = switch (walkIn.status()) {
      case BOOKED -> "El CRS la ha reservado como " + walkIn.locator() + "; baja a Opera y vuelve aquí.";
      case REFUSED -> "Ojo: el CRS no la acepta — " + walkIn.message();
      case PENDING -> "El CRS aún no ha respondido: se le vuelve a enviar sola.";
    };
    return List.of(
        walkIn.status() == io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn.WalkInStatus.REFUSED
            ? Message.error("Estancia " + walkIn.stayId() + " abierta. " + estado)
            : Message.success("Estancia " + walkIn.stayId() + " abierta. " + estado),
        UICommand.navigateTo("/reservas/" + walkIn.stayId()));
  }

  WalkInDesk.Request request(BigDecimal expectedTotal) {
    return new WalkInDesk.Request(null, null, llegada, salida, habitacion, tarifa, regimen, adultos, edades(),
        new WalkInDesk.Holder(trim(nombre), trim(apellidos), trim(email), trim(telefono),
            nacionalidad == null ? null : nacionalidad.trim().toUpperCase(), tipoDocumento, trim(documento)),
        expectedTotal);
  }

  /** Lo que decide el precio: si cambia tras pedirlo, el precio ya no vale. */
  String huella() {
    return llegada + "|" + salida + "|" + habitacion + "|" + tarifa + "|" + regimen + "|" + adultos + "|" + edades();
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

  @Override
  public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
    return WalkInForm.class.equals(formType) && SELECTS.contains(fieldName);
  }

  /** Los códigos del CRS para este hotel, con sus nombres: es el CRS quien los vende. */
  @Override
  public List<Option> options(String fieldName, HttpRequest httpRequest) {
    if ("tipoDocumento".equals(fieldName)) {
      return List.of(new Option("PASSPORT", "Pasaporte"), new Option("ID_CARD", "DNI / documento de identidad"),
          new Option("DRIVING_LICENSE", "Permiso de conducir"));
    }
    var offer = WalkInDesk.desk().offer();
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

  static String trim(String s) {
    return s == null ? null : s.trim();
  }
}
