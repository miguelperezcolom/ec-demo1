package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.infra.mdm.Kardex;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The kárdex of each pax of a stay, as the desk edits it: pax 1 is the stay's guest, the others its
 * companions. What the chain's master keeps of the guest — name, document, contact — is proposed to it
 * as a pending change in the same transaction as the edit, and sent to the MDM once it committed.
 */
@Service
public class KardexService {

  final StayRepository stays;
  final GuestRepository guests;
  final Kardex kardex;
  final TransactionTemplate transaction;

  public KardexService(StayRepository stays, GuestRepository guests, Kardex kardex,
                       PlatformTransactionManager transactions) {
    this.stays = stays;
    this.guests = guests;
    this.kardex = kardex;
    this.transaction = new TransactionTemplate(transactions);
  }

  /** The desk's demo scanner read the pax's document. */
  public void scanned(String stayId, int pax) {
    // A scan never tells the master anything: it only marks the identity as seen.
    transaction.executeWithoutResult(status -> {
      if (pax <= 1) {
        guests.save(guestOf(stayId).scanned());
      } else {
        stays.save(stay(stayId).scanCompanion(pax));
      }
    });
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
    var change = transaction.execute(status -> {
      var before = guestOf(stayId);
      var after = guests.save(edit.apply(before));
      return kardex.record(before, after);
    });
    change.ifPresent(kardex::send);
  }

  Guest guestOf(String stayId) {
    var stay = stay(stayId);
    return guests.findById(stay.guestId()).orElseThrow(() -> new NoSuchElementException("No guest " + stay.guestId()));
  }

  io.mateu.ecdemo1.frontoffice.domain.stay.Stay stay(String stayId) {
    return stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
  }
}
