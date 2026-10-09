package io.mateu.ecdemo1.frontoffice.domain.guest;

import java.util.ArrayList;
import java.util.List;

/**
 * Guest aggregate root — the hotel's cardex: identity, contact data, loyalty standing and
 * preferences. Stays reference guests by id; a guest exists independently of any stay.
 */
public record Guest(
    String id,
    String name,
    String document,
    boolean documentVerified,
    String email,
    String phone,
    GuestTier tier,
    int loyaltyPoints,
    int stays,
    int nights,
    int yearsAsClient,
    int complaints,
    int hotels,
    String lastStaySummary,
    String lastStayComplementaryInfo,
    List<Preference> preferences) {

  public Guest {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Guest id is required");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Guest name is required");
    preferences = preferences == null ? List.of() : List.copyOf(preferences);
  }

  /**
   * Whether a document is one the desk made up to mark the identity as seen — a scan simulated
   * ({@code ESC-…}, before the scanner read documents) or a registration by hand without one ({@code MAN-…}) — and not the guest's.
   */
  public static boolean placeholderDocument(String document) {
    return document != null && (document.startsWith("MAN-") || document.startsWith("ESC-"));
  }

  /** The value of one of the fields the chain's master keeps, by the kardex's name for it. */
  public String valueOf(String field) {
    return switch (field) {
      case "nombre" -> name;
      case "documento" -> document;
      case "email" -> email;
      case "teléfono" -> phone;
      default -> null;
    };
  }

  /** Whether the front desk can skip the identity step of the check-in. */
  public boolean identityComplete() {
    return document != null && !document.isBlank() && documentVerified;
  }

  /** Records a verified identity document (check-in identity step). */
  public Guest verifyIdentity(String document) {
    if (document == null || document.isBlank())
      throw new IllegalArgumentException("A document is required to verify identity");
    return new Guest(
        id, name, document, true, email, phone, tier, loyaltyPoints, stays, nights, yearsAsClient,
        complaints, hotels, lastStaySummary, lastStayComplementaryInfo, preferences);
  }

  /**
   * A guest the hotel learns of from a reservation, by the customer's code in the chain's MDM: the
   * cardex starts with who the reservation says it is. No loyalty is known yet.
   */
  public static Guest fromReservation(String customerId, String name, String document, String email, String phone) {
    return new Guest(customerId, name, document, false, email, phone, GuestTier.SILVER, 0, 0, 0, 0, 0, 0,
        null, null, List.of());
  }

  /**
   * What a later reservation says of the guest fills what the cardex lacks; what the desk verified
   * — a document, contact data taken at check-in — is not overwritten by it.
   */
  /**
   * What the desk took down at the counter for a walk-in, onto the chain's customer the walk-in turned
   * out to be: the document it saw — verified if it verified it —, and the contact it wrote, where it
   * wrote one.
   */
  public Guest withDeskData(Guest desk) {
    var deskDocument = desk.document() != null && !desk.document().isBlank();
    return new Guest(
        id, name, deskDocument ? desk.document() : document, deskDocument ? desk.documentVerified() : documentVerified,
        desk.email() == null || desk.email().isBlank() ? email : desk.email(),
        desk.phone() == null || desk.phone().isBlank() ? phone : desk.phone(),
        tier, loyaltyPoints, stays, nights, yearsAsClient, complaints, hotels, lastStaySummary,
        lastStayComplementaryInfo, preferences);
  }

  public Guest withReservationData(String name, String document, String email, String phone) {
    return new Guest(
        id, name == null || name.isBlank() ? this.name : name,
        this.document == null || this.document.isBlank() ? document : this.document, documentVerified,
        this.email == null || this.email.isBlank() ? email : this.email,
        this.phone == null || this.phone.isBlank() ? phone : this.phone,
        tier, loyaltyPoints, stays, nights, yearsAsClient, complaints, hotels, lastStaySummary,
        lastStayComplementaryInfo, preferences);
  }

  /** The guest's loyalty standing, as the loyalty programme has it. */
  public Guest withLoyalty(GuestTier tier, int loyaltyPoints) {
    return new Guest(
        id, name, document, documentVerified, email, phone, tier == null ? this.tier : tier, loyaltyPoints, stays,
        nights, yearsAsClient, complaints, hotels, lastStaySummary, lastStayComplementaryInfo, preferences);
  }

  /** The name as the desk corrects it. */
  public Guest rename(String name) {
    if (name == null || name.isBlank()) return this;
    return new Guest(
        id, name.trim(), document, documentVerified, email, phone, tier, loyaltyPoints, stays, nights,
        yearsAsClient, complaints, hotels, lastStaySummary, lastStayComplementaryInfo, preferences);
  }

  /**
   * The customer's data as the chain's master (Salesforce, through the MDM) has it: it replaces the
   * cardex's. What only the hotel knows — preferences, stays, whether the document was verified here —
   * stays.
   */
  public Guest withMasterData(String name, String document, String email, String phone) {
    return new Guest(
        id, name == null || name.isBlank() ? this.name : name, document, documentVerified, email, phone,
        tier, loyaltyPoints, stays, nights, yearsAsClient, complaints, hotels, lastStaySummary,
        lastStayComplementaryInfo, preferences);
  }

  /**
   * The desk's scanner read the guest's document: the identity is seen, with the document it read.
   * A document carries no email or phone: the contact stays as it was.
   */
  public Guest scanned(String document) {
    return verifyIdentity(document);
  }

  /**
   * Registered by hand at the desk: document, name and contact in one go. Without a document the
   * identity is still marked as seen, with a made-up {@code MAN-…} one.
   */
  public Guest registeredAtDesk(String document, String name, String email, String phone) {
    var doc = document == null || document.isBlank() ? "MAN-" + id.toUpperCase() : document;
    return rename(name).verifyIdentity(doc).updateContact(email, phone);
  }

  /** Updates the contact data captured at the desk. */
  public Guest updateContact(String email, String phone) {
    return new Guest(
        id, name, document, documentVerified, email, phone, tier, loyaltyPoints, stays, nights,
        yearsAsClient, complaints, hotels, lastStaySummary, lastStayComplementaryInfo, preferences);
  }

  /** Adds a preference to the cardex if not already present. */
  public Guest addPreference(String text) {
    if (preferences.stream().anyMatch(p -> p.text().equalsIgnoreCase(text))) return this;
    List<Preference> updated = new ArrayList<>(preferences);
    updated.add(new Preference(text));
    return new Guest(
        id, name, document, documentVerified, email, phone, tier, loyaltyPoints, stays, nights,
        yearsAsClient, complaints, hotels, lastStaySummary, lastStayComplementaryInfo, updated);
  }
}
