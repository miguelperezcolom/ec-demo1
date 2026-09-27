package io.mateu.ecdemo1.frontoffice.domain.folio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.mateu.ecdemo1.frontoffice.domain.catalog.AddOnCatalogItem;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;

/**
 * Folio aggregate root — the guest account of a stay: the charges posted during the stay and the
 * card pre-authorization taken at check-in. Referenced to the stay by {@code stayId}; the balance
 * is always derived from the lines, never stored.
 */
public record Folio(
    String id,
    String stayId,
    BigDecimal preauthorized,
    List<FolioLine> lines) {

  public Folio {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Folio id is required");
    if (stayId == null || stayId.isBlank())
      throw new IllegalArgumentException("A folio belongs to a stay");
    lines = lines == null ? List.of() : List.copyOf(lines);
  }

  /** What a late check-out costs, and how the folio names it: its line is the whole record of it. */
  public static final BigDecimal LATE_CHECK_OUT_FEE = new BigDecimal("50.00");

  public static final String LATE_CHECK_OUT = "Late check-out (salida 15:00)";

  public static Folio openFor(String id, String stayId, BigDecimal preauthorized) {
    return new Folio(id, stayId, preauthorized, List.of());
  }

  /** The id of a stay's folio: one per stay. */
  public static String idFor(String stayId) {
    return "f-" + stayId;
  }

  /** A folio for a stay with nothing on it and no pre-authorization — to post a charge on. */
  public static Folio emptyFor(String stayId) {
    return openFor(idFor(stayId), stayId, null);
  }

  /**
   * The folio a check-in opens: pre-authorized for the stay's total, with the accommodation and each
   * contracted add-on that has a price (an add-on included in the package costs nothing here).
   */
  public static Folio openAtCheckIn(Stay stay, List<AddOnCatalogItem> contractedAddOns) {
    var folio = openFor(idFor(stay.id()), stay.id(), stay.total())
        .post(FolioLine.charge("Alojamiento x" + stay.nights() + " noches", stay.total()));
    for (var item : contractedAddOns) {
      if (item != null && item.price() != null) {
        folio = folio.post(FolioLine.charge(item.title(), item.price()));
      }
    }
    return folio;
  }

  /** Whether the late check-out is contracted: its charge is on the folio. */
  public boolean lateCheckOutContracted() {
    return lines.stream().anyMatch(l -> l.concept() != null && l.concept().startsWith("Late check-out"));
  }

  /** Contracts the late check-out — charged once: contracted again, the same folio. */
  public Folio contractLateCheckOut() {
    return lateCheckOutContracted() ? this : post(FolioLine.charge(LATE_CHECK_OUT, LATE_CHECK_OUT_FEE));
  }

  /** Outstanding balance: the sum of all line amounts (included lines count as zero). */
  public BigDecimal balance() {
    return lines.stream()
        .map(FolioLine::amount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** Whether the pre-authorization taken at check-in covers the current balance. */
  public boolean coveredByPreauthorization() {
    return preauthorized != null && balance().compareTo(preauthorized) <= 0;
  }

  public Folio post(FolioLine line) {
    List<FolioLine> updated = new ArrayList<>(lines);
    updated.add(line);
    return new Folio(id, stayId, preauthorized, updated);
  }
}
