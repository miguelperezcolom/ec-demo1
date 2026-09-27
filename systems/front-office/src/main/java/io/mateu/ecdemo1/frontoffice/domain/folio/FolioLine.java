package io.mateu.ecdemo1.frontoffice.domain.folio;

import java.math.BigDecimal;

/**
 * A charge (or discount, when negative) posted to a folio. {@code included} lines belong to the
 * contracted package and carry no amount. Value object owned by {@link Folio}.
 */
public record FolioLine(String concept, BigDecimal amount, boolean included, String includedLabel) {

  public static FolioLine charge(String concept, BigDecimal amount) {
    return new FolioLine(concept, amount, false, null);
  }

  public static FolioLine includedInPackage(String concept, String label) {
    return new FolioLine(concept, null, true, label);
  }
}
