package io.mateu.ecdemo1.frontoffice.ui.common;

import io.mateu.ecdemo1.frontoffice.application.Recognition;
import io.mateu.ecdemo1.frontoffice.domain.customer.LoyaltyStatus;
import io.mateu.ecdemo1.frontoffice.domain.customer.PaxRecognitions.MatchedBy;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory.HistorySummary;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory.LastStay;
import io.mateu.uidl.data.BulletedList;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.TextSize;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What the desk is shown of a pax it recognises (see {@link Recognition}): «Cliente conocido» — the
 * summary of their stays in the chain and their Riu Class standing; or «Posible cliente conocido: ¿es
 * usted …?» — only names and birth dates, never a history. A summary, never what they consumed (GDPR).
 * No text here is ever null: Mateu would paint it.
 */
public final class CustomerPanels {

  static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  /** The panel of one pax; an empty layout when nothing is known of them. */
  public static Component panel(Recognition.View view) {
    if (view.known()) {
      return known(view);
    }
    if (view.possible()) {
      return possible(view);
    }
    return VerticalLayout.builder().content(List.of()).build();
  }

  static Component known(Recognition.View view) {
    var content = new ArrayList<Component>();
    var summary = view.stays();
    if (summary.isPresent()) {
      var s = summary.get();
      content.add(Text.builder().text(stays(s)).noMargins(true).build());
      if (!s.lastStays().isEmpty()) {
        content.add(Text.builder().text("Últimas estancias").size(TextSize.xs).noMargins(true).build());
        content.add(BulletedList.builder().items(s.lastStays().stream().map(CustomerPanels::lastStay).toList()).build());
      }
      content.add(Text.builder().text(hotels(s)).size(TextSize.xs).noMargins(true).build());
    } else {
      content.add(Text.builder().text(view.history().isPresent() ? "Sin estancias anteriores en la cadena"
          : "Historial de estancias no disponible").size(TextSize.xs).noMargins(true).build());
    }
    view.loyalty().ifPresent(l -> content.add(Text.builder().text(loyalty(l)).noMargins(true).build()));
    content.add(Text.builder().text("Reconocido " + by(view.matchedBy())).size(TextSize.xs).noMargins(true).build());
    return Notice.builder()
        .theme("success")
        .icon("★")
        .text("Cliente conocido — " + orBlank(view.name(), view.customerId()))
        .fullWidth(true)
        .content(content)
        .build();
  }

  static Component possible(Recognition.View view) {
    var content = new ArrayList<Component>();
    if (view.candidates().isEmpty()) {
      content.add(Text.builder()
          .text("Su documento coincide con más de un cliente de la cadena.")
          .size(TextSize.xs).noMargins(true).build());
    } else {
      content.add(BulletedList.builder().items(view.candidates().stream()
          .map(c -> orBlank(c.name(), c.customerId())
              + (c.birthDate() == null ? "" : " · nacido/a el " + c.birthDate().format(DAY)))
          .toList()).build());
    }
    content.add(Text.builder()
        .text("Confirme con su número Riu Class o su email para ver su historial.")
        .size(TextSize.xs).noMargins(true).build());
    return Notice.builder()
        .theme("warning")
        .icon("?")
        .text("Posible cliente conocido: ¿es usted " + (view.candidates().size() == 1
            ? orBlank(view.candidates().getFirst().name(), "este cliente") + "?" : "uno de estos clientes?"))
        .fullWidth(true)
        .content(content)
        .build();
  }

  /** One line for the guests' rail; empty when nothing is known of the pax. */
  public static Optional<String> railLine(Recognition.View view) {
    if (view.known()) {
      return Optional.of("★ Cliente conocido" + view.stays().map(s -> " · " + stays(s)).orElse("")
          + view.loyalty().map(l -> " · " + l.tier()).orElse(""));
    }
    if (view.possible()) {
      return Optional.of("¿Posible cliente conocido? Confirmar con Riu Class o email");
    }
    return Optional.empty();
  }

  /** «5 estancias · 23 noches». */
  public static String stays(HistorySummary s) {
    return s.stays() + (s.stays() == 1 ? " estancia" : " estancias") + " · " + s.nights()
        + (s.nights() == 1 ? " noche" : " noches");
  }

  /** «MRU01 · 01/10/2026 → 05/10/2026 · Hab 1204 (Doble)». */
  public static String lastStay(LastStay l) {
    var line = new StringBuilder(orBlank(l.hotelCode(), "Hotel"));
    if (l.arrival() != null) {
      line.append(" · ").append(l.arrival().format(DAY));
      if (l.departure() != null) {
        line.append(" → ").append(l.departure().format(DAY));
      }
    }
    if (l.roomNumber() != null) {
      line.append(" · Hab ").append(l.roomNumber());
    }
    if (l.roomType() != null) {
      line.append(l.roomNumber() == null ? " · " : " (").append(l.roomType()).append(l.roomNumber() == null ? "" : ")");
    }
    return line.toString();
  }

  /** «3 hoteles · el más repetido: MRU01». */
  static String hotels(HistorySummary s) {
    return s.hotels() + (s.hotels() == 1 ? " hotel" : " hoteles")
        + (s.topHotel() == null ? "" : " · el más repetido: " + s.topHotel())
        + (s.firstStay() == null ? "" : " · cliente desde " + s.firstStay().getYear());
  }

  /** «Riu Class GOLD · 12.500 puntos · socio RC12345678». */
  public static String loyalty(LoyaltyStatus.Loyalty l) {
    return "Riu Class " + orBlank(l.tier(), "") + " · " + points(l.points()) + " puntos"
        + (l.memberNumber() == null ? "" : " · socio " + l.memberNumber())
        + (l.asOf() == null ? "" : " · a " + l.asOf().format(DAY));
  }

  public static String points(int points) {
    return String.format(Locale.US, "%,d", points).replace(',', '.');
  }

  /** The last stay of the summary, as the profile's «Última estancia» says it. */
  public static Optional<String> lastStay(HistorySummary s) {
    return s.lastStays().stream().findFirst().map(CustomerPanels::lastStay);
  }

  /** «Cliente desde 2019», from the first stay. */
  public static String since(HistorySummary s, LocalDate fallback) {
    var first = s.firstStay() != null ? s.firstStay() : fallback;
    return first == null ? "" : " · Cliente desde " + first.getYear();
  }

  static String by(MatchedBy by) {
    if (by == null) {
      return "";
    }
    return switch (by) {
      case DOCUMENT -> "por su documento";
      case EMAIL -> "por su email (confirmado en recepción)";
      case RIU_CLASS -> "por su número Riu Class (confirmado en recepción)";
      case CANDIDATE -> "por su nombre y fecha de nacimiento";
      case CHAIN_CODE -> "por la reserva: ya es cliente de la cadena";
    };
  }

  static String orBlank(String value, String otherwise) {
    return value == null || value.isBlank() ? (otherwise == null ? "" : otherwise) : value;
  }

  private CustomerPanels() {}
}
