package io.mateu.ecdemo1.frontoffice.ui.walkin;

import io.mateu.core.infra.declarative.orchestrators.wizard.Wizard;
import io.mateu.ecdemo1.frontoffice.application.WalkInService;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn;
import io.mateu.ecdemo1.frontoffice.infra.crs.WalkInDesk;
import io.mateu.ecdemo1.frontoffice.ui.common.GuestHeaders;
import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.WizardCompletionAction;
import io.mateu.uidl.annotations.WizardProgress;
import io.mateu.uidl.annotations.WizardProgressStyle;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.interfaces.HttpRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Un cliente sin reserva en recepción, paso a paso: la estancia; el precio, que pone el CRS — es
 * quien vende — y se le pide al entrar en el paso; el titular; y el resumen, donde «Confirmar
 * walk-in» abre la estancia aquí mismo — para hacer el check-in ya — y pide al CRS la reserva a ese
 * precio, que baja después a Opera y vuelve a esta estancia (el caso de uso {@link WalkInService}).
 *
 * <p>Un bean prototipo, como su primer paso: Mateu los toma de Spring y los hidrata en cada petición.
 * El estado del asistente es un mapa plano: los campos de los pasos no repiten nombre.
 */
@Service
@Scope("prototype")
@Title("Walk-in")
@Style(StyleConstants.CONTAINER)
@WizardProgress(WizardProgressStyle.STEPS)
public class WalkInWizard extends Wizard {

  @Label("Estancia")
  EstanciaWalkIn estancia;

  @Label("Precio del CRS")
  PrecioWalkIn precio;

  @Label("Titular")
  TitularWalkIn titular;

  @Label("Confirmar")
  ConfirmarWalkIn confirmar;

  @Label("Walk-in abierto")
  AbiertaWalkIn abierta;

  final WalkInService walkIns;

  public WalkInWizard(WalkInService walkIns) {
    this.walkIns = walkIns;
  }

  @Override
  public Object handleAction(String actionId, HttpRequest httpRequest) {
    if ("next".equals(actionId)) {
      var problema = problemaAlSalir(currentStepField().getName(), httpRequest);
      if (problema != null) {
        return Message.error(problema);
      }
    }
    var result = super.handleAction(actionId, httpRequest);
    if ("next".equals(actionId)) {
      switch (currentStepField().getName()) {
        case "precio" -> cotizar();
        case "confirmar" -> resumir();
        default -> {
        }
      }
    }
    return result;
  }

  /** Lo que impide dejar el paso actual — lo que el paso no valida por sí solo —, o null. */
  String problemaAlSalir(String paso, HttpRequest httpRequest) {
    return switch (paso) {
      case "estancia" -> estancia == null ? null : estancia.problema();
      case "precio" -> precio != null && precio.cotizado() && vigente() ? null
          : "Sin precio del CRS no se puede seguir: vuelve atrás y cambia la estancia.";
      case "titular" -> {
        if (titular == null) {
          yield "Falta del titular: nombre, apellidos, documento.";
        }
        var falta = WalkInService.missing(holder());
        yield falta.isEmpty() ? null : "Falta del titular: " + String.join(", ", falta) + ".";
      }
      default -> null;
    };
  }

  /** Pide el precio al CRS para la estancia del paso 1 — cada vez que se entra en el paso. */
  void cotizar() {
    if (precio == null) {
      precio = new PrecioWalkIn();
    }
    try {
      var quote = walkIns.quote(request(null));
      precio.setPresupuesto(quote.total());
      precio.setPresupuestoDe(estancia.huella());
      precio.setErrorCrs(null);
      precio.setNoches(quote.nights() + (quote.nights() == 1 ? " noche" : " noches"));
      precio.setPrecioNoche(quote.nights() > 0
          ? importe(quote.total().divide(BigDecimal.valueOf(quote.nights()), 2, RoundingMode.HALF_UP), quote.currency())
          : "—");
      precio.setTotalCrs(importe(quote.total(), quote.currency()));
    } catch (RuntimeException e) {
      precio.setPresupuesto(null);
      precio.setPresupuestoDe(null);
      precio.setErrorCrs(e.getMessage());
      precio.setNoches("—");
      precio.setPrecioNoche("—");
      precio.setTotalCrs("—");
    }
  }

  /** El resumen del paso Confirmar, de lo que dicen los pasos anteriores. */
  void resumir() {
    if (confirmar == null) {
      confirmar = new ConfirmarWalkIn();
    }
    var offer = oferta();
    confirmar.setResumenEstancia(estancia.getLlegada() + " → " + estancia.getSalida() + " · "
        + nombre(offer == null ? null : offer.roomTypes(), estancia.getHabitacion()) + " · "
        + nombre(offer == null ? null : offer.ratePlans(), estancia.getTarifa()) + " · "
        + nombre(offer == null ? null : offer.boards(), estancia.getRegimen()));
    var edades = estancia.edades();
    confirmar.setResumenHuespedes(estancia.getAdultos() + (estancia.getAdultos() == 1 ? " adulto" : " adultos")
        + (edades.isEmpty() ? "" : " · niños de " + edades.stream().map(String::valueOf)
            .reduce((a, b) -> a + ", " + b).orElse("") + " años"));
    confirmar.setResumenPrecio(precio == null ? "—" : precio.getTotalCrs() + " · " + precio.getNoches());
    var h = holder();
    confirmar.setResumenTitular(h.fullName().trim()
        + (blank(h.email()) ? "" : " · " + h.email()) + (blank(h.phone()) ? "" : " · " + h.phone()));
    confirmar.setResumenDocumento((h.documentType() == null ? "" : h.documentType() + " ")
        + (h.documentNumber() == null ? "" : h.documentNumber())
        + (blank(h.nationality()) ? "" : " · " + h.nationality()));
  }

  /** Abre la estancia y pide la reserva al CRS al precio que dio; lleva a la estancia abierta. */
  @WizardCompletionAction
  @Label("Confirmar walk-in")
  public Object confirmarWalkIn() {
    if (precio == null || !precio.cotizado() || !vigente()) {
      return Message.warning("Calcula el precio antes de confirmar: el CRS tiene que decir cuánto cuesta esto que se confirma.");
    }
    WalkIn walkIn;
    try {
      walkIn = walkIns.confirm(request(precio.getPresupuesto()), precio.getPresupuesto());
    } catch (IllegalArgumentException e) {
      return Message.warning(e.getMessage());
    }
    var estado = switch (walkIn.status()) {
      case BOOKED -> "El CRS la ha reservado como " + walkIn.locator() + "; baja a Opera y vuelve aquí.";
      case REFUSED -> "Ojo: el CRS no la acepta — " + walkIn.message();
      case PENDING -> "El CRS aún no ha respondido: se le vuelve a enviar sola.";
    };
    return List.of(
        walkIn.status() == WalkIn.WalkInStatus.REFUSED
            ? Message.error("Estancia " + walkIn.stayId() + " abierta. " + estado)
            : Message.success("Estancia " + walkIn.stayId() + " abierta. " + estado),
        UICommand.navigateTo("/reservas/" + walkIn.stayId()));
  }

  /** El precio es de esta estancia: no ha cambiado desde que se pidió. */
  boolean vigente() {
    return estancia != null && precio != null && Objects.equals(precio.getPresupuestoDe(), estancia.huella());
  }

  WalkInDesk.Request request(BigDecimal expectedTotal) {
    return new WalkInDesk.Request(null, null, estancia.getLlegada(), estancia.getSalida(), estancia.getHabitacion(),
        estancia.getTarifa(), estancia.getRegimen(), estancia.getAdultos(), estancia.edades(), holder(), expectedTotal);
  }

  WalkInDesk.Holder holder() {
    var t = titular == null ? new TitularWalkIn() : titular;
    return new WalkInDesk.Holder(trim(t.getNombre()), trim(t.getApellidos()), trim(t.getEmail()),
        trim(t.getTelefono()), t.getNacionalidad() == null ? null : t.getNacionalidad().trim().toUpperCase(),
        t.getTipoDocumento(), trim(t.getDocumento()));
  }

  WalkInDesk.Offer oferta() {
    try {
      return walkIns.offer();
    } catch (RuntimeException e) {
      return null;
    }
  }

  static String nombre(List<WalkInDesk.Option> options, String code) {
    if (options == null || code == null) {
      return code;
    }
    return options.stream().filter(o -> o.code().equals(code)).map(o -> o.name() + " (" + code + ")")
        .findFirst().orElse(code);
  }

  static String importe(BigDecimal amount, String currency) {
    return GuestHeaders.euros(amount).replace("€ ", "") + " " + (currency == null ? "EUR" : currency);
  }

  static String trim(String s) {
    return s == null ? null : s.trim();
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
