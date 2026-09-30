package io.mateu.ecdemo1.frontoffice.domain.stay;

/**
 * One step a check-in still lacks: a pax's identity document (what the traveller's registration —
 * Spain's «parte de viajeros» — is made of) or the guest's signature on the registration card. Without
 * them the check-in is refused unless the desk forces it, with a reason, and completes them later.
 *
 * @param pax the pax it is about (1 is the holder); 0 for the signature, which is the stay's
 */
public record PendingStep(Kind kind, int pax, String label) {

  public enum Kind { DOCUMENT, SIGNATURE, REGISTRATION }

  public static PendingStep document(int pax, String name) {
    return new PendingStep(Kind.DOCUMENT, pax, "Documento de " + name + " (pax " + pax + ")");
  }

  /**
   * Data the destination's registration rules require of a pax and the kárdex does not have yet
   * ({@code missing}, in the desk's words), and why ({@code legalBasis}, may be empty).
   */
  public static PendingStep registration(int pax, String name, java.util.List<String> missing, String legalBasis) {
    return new PendingStep(Kind.REGISTRATION, pax, "Datos de registro de " + name + " (pax " + pax + "): "
        + String.join(", ", missing) + (legalBasis == null || legalBasis.isBlank() ? "" : " — " + legalBasis));
  }

  public static PendingStep signature() {
    return new PendingStep(Kind.SIGNATURE, 0, "Firma del registro (tablet)");
  }

  public boolean document() {
    return kind == Kind.DOCUMENT;
  }
}
