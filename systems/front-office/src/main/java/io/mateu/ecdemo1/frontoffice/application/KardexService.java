package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.mdm.Kardex;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import io.mateu.ecdemo1.frontoffice.infra.scanner.DemoDocuments;
import io.mateu.ecdemo1.frontoffice.infra.scanner.DemoScanner;
import io.mateu.ecdemo1.integration.model.command.CustomerCommand;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The kárdex of each pax of a stay, as the desk edits it: pax 1 is the stay's guest, the others its
 * companions. What the chain's master keeps of the guest — name, document, contact — is proposed to it
 * as a pending change; a document the desk scans goes to the chain's MDM as trusted data. Either one as
 * a command in the outbox, in the same transaction as the edit: the desk never waits for the MDM.
 */
@Service
public class KardexService {

  final StayRepository stays;
  final GuestRepository guests;
  final Kardex kardex;
  final DemoScanner scanner;
  final WalkIns walkIns;
  final CommandOutbox outbox;
  final String hotel;
  final TransactionTemplate transaction;

  public KardexService(StayRepository stays, GuestRepository guests, Kardex kardex, DemoScanner scanner,
                       WalkIns walkIns, CommandOutbox outbox, @Value("${frontoffice.hotel:MRU01}") String hotel,
                       PlatformTransactionManager transactions) {
    this.stays = stays;
    this.guests = guests;
    this.kardex = kardex;
    this.scanner = scanner;
    this.walkIns = walkIns;
    this.outbox = outbox;
    this.hotel = hotel;
    this.transaction = new TransactionTemplate(transactions);
  }

  /**
   * The desk's scanner read the pax's document: the identity is seen, with that document, and the
   * document — number, name, birth date, nationality — goes to the chain's MDM, with the reservation
   * and the pax it belongs to. Holder and companions alike.
   */
  public DemoDocuments.Scanned scanned(String stayId, int pax) {
    var stay = stay(stayId);
    var guest = pax <= 1 ? guestOf(stayId) : null;
    var companion = pax <= 1 ? null : stay.companionAt(pax);
    var name = pax <= 1 ? guest.name() : companion == null ? Companion.pending(pax).name() : companion.name();
    var current = pax <= 1 ? guest.document() : companion == null ? null : companion.document();
    var customerId = pax <= 1 ? guest.id() : companion == null ? null : companion.companionId();
    var locator = locatorOf(stayId);
    // The scanner reads before the transaction: it may ask the booking and the MDM, and nobody waits on a lock for it.
    var document = scanner.scan(new DemoScanner.Pax(locator, pax, name, current, customerId, stay.checkIn()));
    transaction.executeWithoutResult(status -> {
      if (pax <= 1) {
        guests.save(guestOf(stayId).scanned(document.documentNumber()));
      } else {
        stays.save(stay(stayId).scanCompanion(pax, document.documentNumber()));
      }
      var commandId = "SCAN-" + UUID.randomUUID();
      var command = new CustomerCommand.RecordScannedIdentity(commandId, hotel, locator, stayId, pax,
          customerId != null && customerId.startsWith("C-") ? customerId : null, document.firstName(),
          document.lastName(), document.documentType(), document.documentNumber(), document.birthDate(),
          document.nationality(), "front office " + hotel + " · " + stayId + " pax " + pax);
      outbox.append(CommandOutbox.CUSTOMER_COMMANDS, command.key(), command);
    });
    return document;
  }

  /** The CRS's locator of a stay: its id, or — for a walk-in — the one the CRS gave it (the stay's own until then). */
  String locatorOf(String stayId) {
    return walkIns.of(stayId).map(w -> w.locator() == null ? stayId : w.locator()).orElse(stayId);
  }

  /** The pax registered — or corrected — by hand: document, name and contact. */
  public void registered(String stayId, int pax, String document, String name, String email, String phone) {
    if (pax <= 1) {
      guestEdited(stayId, g -> g.registeredAtDesk(document, name, email, phone));
      return;
    }
    transaction.executeWithoutResult(status ->
        stays.save(stay(stayId).registerCompanionAtDesk(pax, document, name, email, phone)));
  }

  /** The pax's contact, as the desk took it down. */
  public void contactUpdated(String stayId, int pax, String email, String phone) {
    if (pax <= 1) {
      guestEdited(stayId, g -> g.updateContact(email, phone));
      return;
    }
    transaction.executeWithoutResult(status ->
        stays.save(stay(stayId).updateCompanionContact(pax, email, phone)));
  }

  /** The guest's last change to the master's data, if the desk ever made one. */
  public Optional<KardexChange> changeOf(String guestId) {
    return kardex.of(guestId);
  }

  void guestEdited(String stayId, UnaryOperator<Guest> edit) {
    transaction.executeWithoutResult(status -> {
      var before = guestOf(stayId);
      var after = guests.save(edit.apply(before));
      kardex.record(before, after);
    });
  }

  Guest guestOf(String stayId) {
    var stay = stay(stayId);
    return guests.findById(stay.guestId()).orElseThrow(() -> new NoSuchElementException("No guest " + stay.guestId()));
  }

  io.mateu.ecdemo1.frontoffice.domain.stay.Stay stay(String stayId) {
    return stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
  }
}
