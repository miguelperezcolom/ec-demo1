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
 * The cardex and the chain's master of customers. The desk's changes to a guest's data are kept here
 * at once and shown as pending, and sent to the MDM, which takes them to Salesforce — the master —
 * to be decided. What the master decides, and any change it makes, comes back through the MDM:
 * approved, the data stays; rejected, the master's comes back.
 */
@Slf4j
@Service
public class Kardex {

  private static Kardex instance;

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
    instance = this;
  }

  /** The desk changed a guest: if what the master keeps changed, it is proposed to it. */
  public static void edited(Guest before, Guest after) {
    if (instance != null) {
      instance.onEdit(before, after);
    }
  }

  public static Optional<KardexChange> of(String guestId) {
    return instance == null ? Optional.empty() : instance.changes.of(guestId);
  }

  void onEdit(Guest before, Guest after) {
    if (before == null || !after.masterDataDiffers(before) || !isChainCustomer(after.id())) {
      return;
    }
    var change = KardexChange.pending(after.id(), describe(before, after), clock.instant());
    changes.save(change);
    log.info("{}: {} — pending the master's approval", after.id(), change.changes());
    send(change);
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

  void send(KardexChange change) {
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
      proposal.put("documentNumber", guest.document());
      proposal.put("origin", "front office " + hotel);
      var answer = mdm.post().uri("/customers/{id}/change-requests", guest.id()).body(proposal).retrieve()
          .body(java.util.Map.class);
      var sent = change.sent(answer == null || answer.get("id") == null ? null : String.valueOf(answer.get("id")));
      if (answer != null && "APPROVED".equals(answer.get("status"))) {
        // Nothing differs from what the master has: nothing to decide.
        sent = sent.decided(KardexChange.KardexStatus.APPROVED, clock.instant());
      }
      changes.save(sent);
      log.info("{}: change sent to the MDM as {}", guest.id(), sent.requestId());
    } catch (RuntimeException e) {
      log.warn("{}: the MDM did not take the change yet ({}); it goes again", guest.id(), e.getMessage());
    }
  }

  /** The customer's data as the master has it, and — when it answers the desk's change — its decision. */
  public record Update(String name, String email, String phone, String document, String requestId, String decision) {
  }

  /**
   * What the MDM says of a guest. A decision on the pending change closes it: approved, the master's
   * data (the desk's, as approved); rejected, the master's as it was. Without a decision it is a change
   * made in the master: it replaces the cardex's data — unless the desk has a change pending, which
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
      guests.save(guest.withMasterData(u.name(), u.document(), u.email(), u.phone()));
      changes.save(current.decided(decision, clock.instant()));
      log.info("{}: the master {} the desk's change", guestId, decision == KardexChange.KardexStatus.APPROVED ? "approved" : "rejected");
    } else if (current == null || !current.pending()) {
      guests.save(guest.withMasterData(u.name(), u.document(), u.email(), u.phone()));
    }
    return true;
  }

  static String describe(Guest before, Guest after) {
    var changed = new ArrayList<String>();
    diff(changed, "nombre", before.name(), after.name());
    diff(changed, "documento", before.document(), after.document());
    diff(changed, "email", before.email(), after.email());
    diff(changed, "teléfono", before.phone(), after.phone());
    return String.join(", ", changed);
  }

  static void diff(java.util.List<String> changed, String label, String a, String b) {
    if (!Objects.equals(a, b)) {
      changed.add(label + " " + (a == null ? "—" : a) + " → " + (b == null ? "—" : b));
    }
  }
}
