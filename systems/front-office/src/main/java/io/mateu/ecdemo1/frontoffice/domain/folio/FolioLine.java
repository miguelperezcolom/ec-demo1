package io.mateu.ecdemo1.frontoffice.domain.folio;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * A charge (or discount, when negative) posted to a folio. {@code included} lines belong to the
 * contracted package and carry no amount. Value object owned by {@link Folio}.
 *
 * <p>A line the desk charges has an {@code id} of its own — what the PMS's posting of it is found by
 * again (its idempotency key) and what a void names — and says what it is ({@code kind}, and its
 * {@code code} in the desk's catalogues). A {@code voided} line stays on the folio, and counts for
 * nothing. Lines from before charges went up to the PMS have neither id nor kind: they stay the front
 * office's.
 */
public record FolioLine(String id, String concept, BigDecimal amount, boolean included, String includedLabel,
                        ChargeKind kind, String code, boolean voided) {

  /** A line with no id nor kind: the front office's own, never posted to the PMS. */
  public FolioLine(String concept, BigDecimal amount, boolean included, String includedLabel) {
    this(null, concept, amount, included, includedLabel, null, null, false);
  }

  public static FolioLine charge(String concept, BigDecimal amount) {
    return new FolioLine(concept, amount, false, null);
  }

  public static FolioLine includedInPackage(String concept, String label) {
    return new FolioLine(concept, null, true, label);
  }

  /** The stay's nights: the PMS charges them itself. */
  public static FolioLine accommodation(String concept, BigDecimal amount) {
    return new FolioLine(newId(), concept, amount, false, null, ChargeKind.ACCOMMODATION, null, false);
  }

  /** A charge of the desk that goes onto the PMS's folio. */
  public static FolioLine charged(ChargeKind kind, String code, String concept, BigDecimal amount) {
    return new FolioLine(newId(), concept, amount, false, null, kind, code, false);
  }

  /** A new line id: short, unique, fit for the PMS's posting reference ({@code FO:L-7F3A2C1D}). */
  public static String newId() {
    return "L-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
  }

  /** Whether the line goes onto the PMS's folio: a charge of the desk, with an amount, and an id to post it by. */
  public boolean toThePms() {
    return id != null && kind != null && kind.toThePms() && amount != null && !included;
  }

  /** Whether it adds to the folio's balance. */
  public boolean counts() {
    return !voided && amount != null && !included;
  }

  /** The same line, taken back: on the folio, counting for nothing. */
  public FolioLine asVoided() {
    return new FolioLine(id, concept, amount, included, includedLabel, kind, code, true);
  }
}
