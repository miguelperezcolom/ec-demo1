package io.mateu.ecdemo1.frontoffice.infra.mdm;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChanges;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

/**
 * The kardex and the chain's master of customers. The desk's changes to a guest's data are kept here
 * at once and shown as pending, and sent to the MDM, which takes them to Salesforce — the master —
 * to be decided. What the master decides, and any change it makes, comes back through the MDM:
 * approved, the data stays; rejected, the master's comes back.
 */
@Slf4j
@Service
public class Kardex {

  final KardexChanges changes;
  final GuestRepository guests;
  final RestClient mdm;
  final String hotel;
  final Clock clock = Clock.systemUTC();

  public Kardex(KardexChanges changes, GuestRepository guests, @Value("${frontoffice.mdm-url:}") String mdmUrl,
                @Value("${frontoffice.hotel:MRU01}") String hotel) {
    this.changes = changes;
    this.guests = guests;
    this.mdm = mdmUrl == null || mdmUrl.isBlank() ? null : RestClient.builder().baseUrl(mdmUrl).build();
    this.hotel = hotel;
  }

  /**
   * The desk changed a guest: if what the master keeps changed, the change is kept as pending and
   * proposed to it at once. {@link #record} and {@link #send} apart, for a caller that has to send
   * only once its own transaction committed.
   */
  public void edited(Guest before, Guest after) {
    record(before, after).ifPresent(this::send);
  }

  /** The guest's last change to the master's data, if the desk ever made one. */
  public Optional<KardexChange> of(String guestId) {
    return changes.of(guestId);
  }

  /**
   * Keeps what the desk changed of what the master keeps as a change pending its decision — nothing if
   * the guest is not the chain's or nothing the master keeps changed. It is not sent yet: see {@link #send}.
   */
  public Optional<KardexChange> record(Guest before, Guest after) {
    if (before == null || !isChainCustomer(after.id())) {
      return Optional.empty();
    }
    var fields = fieldsChanged(before, after);
    if (fields.isEmpty()) {
      return Optional.empty();
    }
    var change = KardexChange.pending(after.id(), fields, clock.instant());
    changes.save(change);
    log.info("{}: {} — pending the master's approval", after.id(), change.changes());
    return Optional.of(change);
  }

  /** A guest known to the chain's MDM — one the integration brought, by its customer code. */
  static boolean isChainCustomer(String guestId) {
    return guestId != null && guestId.startsWith("C-");
  }

  /** What was edited while the MDM did not answer goes now. */
  @Scheduled(fixedDelayString = "${frontoffice.kardex-resend:15s}")
  public void resend() {
    changes.unsynced().forEach(this::send);
  }

  /** Proposes the change to the MDM; one it does not take now goes again with {@link #resend}. */
  public void send(KardexChange change) {
    if (mdm == null) {
      return;
    }
    var guest = guests.findById(change.guestId()).orElse(null);
    if (guest == null) {
      return;
    }
    try {
      var proposal = new HashMap<String, Object>();
      proposal.put("name", guest.name());
      proposal.put("email", guest.email());
      proposal.put("phone", guest.phone());
      // A document the desk made up is not the guest's: the master's stays.
      proposal.put("documentNumber", Guest.placeholderDocument(guest.document()) ? null : guest.document());
      proposal.put("origin", "front office " + hotel);
      var answer = mdm.post().uri("/customers/{id}/change-requests", guest.id()).body(proposal).retrieve()
          .body(java.util.Map.class);
      var sent = change.sent(answer == null || answer.get("id") == null ? null : String.valueOf(answer.get("id")));
      if (answer != null && "APPROVED".equals(answer.get("status"))) {
        // Nothing differs from what the master has: nothing to decide.
        sent = sent.decided(KardexChange.KardexStatus.APPROVED, null, clock.instant());
      }
      changes.save(sent);
      log.info("{}: change sent to the MDM as {}", guest.id(), sent.requestId());
    } catch (RuntimeException e) {
      log.warn("{}: the MDM did not take the change yet ({}); it goes again", guest.id(), e.getMessage());
    }
  }

  /**
   * The customer's data as the master has it, and — when it answers the desk's change — its decision,
   * and why, if it rejected it and said.
   */
  public record Update(String name, String email, String phone, String document, String requestId, String decision,
                       String reason) {
  }

  /**
   * What the MDM says of a guest. A decision on the pending change closes it: approved, the master's
   * data (the desk's, as approved); rejected, the master's as it was. Without a decision it is a change
   * made in the master: it replaces the kardex's data — unless the desk has a change pending, which
   * stays on screen until it is decided.
   *
   * @return false if the front office does not have this guest
   */
  public boolean projected(String guestId, Update u) {
    var guest = guests.findById(guestId).orElse(null);
    if (guest == null) {
      return false;
    }
    var current = changes.of(guestId).orElse(null);
    var answersTheDesk = u.requestId() != null && current != null && current.pending()
        && (current.requestId() == null || u.requestId().equals(current.requestId()));
    if (answersTheDesk) {
      var decision = "REJECTED".equals(u.decision()) ? KardexChange.KardexStatus.REJECTED : KardexChange.KardexStatus.APPROVED;
      guests.save(guest.withMasterData(u.name(), masterDocument(guest, u), u.email(), u.phone()));
      changes.save(current.decided(decision, u.reason(), clock.instant()));
      log.info("{}: the master {} the desk's change", guestId, decision == KardexChange.KardexStatus.APPROVED ? "approved" : "rejected");
    } else if (current == null || !current.pending()) {
      guests.save(guest.withMasterData(u.name(), masterDocument(guest, u), u.email(), u.phone()));
    }
    return true;
  }

  /**
   * The master has no document for the guest and the desk marked the identity as seen with one it
   * made up: that one stays, since it is the desk's and not a document the master could hold.
   */
  static String masterDocument(Guest guest, Update u) {
    return (u.document() == null || u.document().isBlank()) && Guest.placeholderDocument(guest.document())
        ? guest.document() : u.document();
  }

  /** What the desk changed of what the master keeps, field by field. A made-up document is no change. */
  static java.util.List<KardexChange.FieldChange> fieldsChanged(Guest before, Guest after) {
    var changed = new ArrayList<KardexChange.FieldChange>();
    diff(changed, "nombre", before.name(), after.name());
    diff(changed, "documento", masterValue(before.document()), masterValue(after.document()));
    diff(changed, "email", before.email(), after.email());
    diff(changed, "teléfono", before.phone(), after.phone());
    return changed;
  }

  static String masterValue(String document) {
    return Guest.placeholderDocument(document) ? null : document;
  }

  static void diff(java.util.List<KardexChange.FieldChange> changed, String field, String a, String b) {
    if (!Objects.equals(blankAsNull(a), blankAsNull(b))) {
      changed.add(new KardexChange.FieldChange(field, a, b));
    }
  }

  static String blankAsNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
