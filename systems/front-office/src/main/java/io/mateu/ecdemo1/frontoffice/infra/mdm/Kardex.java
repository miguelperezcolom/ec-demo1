package io.mateu.ecdemo1.frontoffice.infra.mdm;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChanges;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import io.mateu.ecdemo1.integration.model.command.CustomerCommand;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The kardex and the chain's master of customers. The desk's changes to a guest's data are kept here
 * at once and shown as pending, and proposed to the MDM — which takes them to Salesforce, the master,
 * to be decided — as a command in the front office's outbox, written in the same transaction as the
 * edit. The request's id is the front office's: a command delivered twice is one request. What the
 * master decides, and any change it makes, comes back through the MDM: approved, the data stays;
 * rejected, the master's comes back.
 */
@Slf4j
@Service
public class Kardex {

  final KardexChanges changes;
  final GuestRepository guests;
  final CommandOutbox outbox;
  final String hotel;
  final Clock clock = Clock.systemUTC();

  public Kardex(KardexChanges changes, GuestRepository guests, CommandOutbox outbox,
                @Value("${frontoffice.hotel:MRU01}") String hotel) {
    this.changes = changes;
    this.guests = guests;
    this.outbox = outbox;
    this.hotel = hotel;
  }

  /** The desk changed a guest: if what the master keeps changed, the change is kept as pending and proposed to it. */
  public void edited(Guest before, Guest after) {
    record(before, after);
  }

  /** The guest's last change to the master's data, if the desk ever made one. */
  public Optional<KardexChange> of(String guestId) {
    return changes.of(guestId);
  }

  /**
   * Keeps what the desk changed of what the master keeps as a change pending its decision, and proposes
   * it to the MDM through the outbox — in the caller's transaction — nothing if the guest is not the
   * chain's or nothing the master keeps changed.
   */
  public Optional<KardexChange> record(Guest before, Guest after) {
    if (before == null || !isChainCustomer(after.id())) {
      return Optional.empty();
    }
    var fields = fieldsChanged(before, after);
    if (fields.isEmpty()) {
      return Optional.empty();
    }
    var change = propose(KardexChange.pending(after.id(), fields, clock.instant()), after);
    changes.save(change);
    log.info("{}: {} — proposed to the master as {}", after.id(), change.changes(), change.requestId());
    return Optional.of(change);
  }

  /** A guest known to the chain's MDM — one the integration brought, by its customer code. */
  static boolean isChainCustomer(String guestId) {
    return guestId != null && guestId.startsWith("C-");
  }

  /**
   * A change kept before the front office had an outbox, and never taken by the MDM: it goes now, the
   * same way. Once — the change is marked as sent in the same transaction.
   */
  @Scheduled(fixedDelayString = "${frontoffice.kardex-resend:15s}")
  @Transactional
  public void resend() {
    for (var change : changes.unsynced()) {
      var guest = guests.findById(change.guestId()).orElse(null);
      if (guest != null) {
        changes.save(propose(change, guest));
      }
    }
  }

  /** The change as the MDM's command, into the outbox; the change, with its request id, sent. */
  KardexChange propose(KardexChange change, Guest guest) {
    var requestId = change.requestId() != null ? change.requestId()
        : "CR-FO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    // A document the desk made up is not the guest's: the master's stays.
    var document = Guest.placeholderDocument(guest.document()) ? null : guest.document();
    outbox.append(CommandOutbox.CUSTOMER_COMMANDS, guest.id(), requestId, new CustomerCommand.ProposeChange(requestId,
        guest.id(), guest.name(), guest.email(), guest.phone(), document, "front office " + hotel));
    return change.sent(requestId);
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
