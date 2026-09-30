package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.catalog.AddOnCatalogRepository;
import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOps;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.SelectedAddOn;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.infra.pms.ReceptionReports;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The check-in of an arriving stay, and the desk's operations around it — each one transaction: a
 * check-in that fails halfway leaves the stay, its room, its folio and its operations as they were.
 * The check-in goes up to the PMS, the master of the stay, in the same transaction (an event in the
 * outbox, {@link ReceptionReports}).
 */
@Service
public class CheckInService {

  final StayRepository stays;
  final RoomRepository rooms;
  final FolioRepository folios;
  final AddOnCatalogRepository addOnCatalog;
  final CheckInOpsRepository checkInOps;
  final GuestNotices notices;
  final ReceptionReports reception;
  final IncompleteCheckIns incomplete;
  final StayAudit audit;

  public CheckInService(StayRepository stays, RoomRepository rooms, FolioRepository folios,
                        AddOnCatalogRepository addOnCatalog, CheckInOpsRepository checkInOps, GuestNotices notices,
                        ReceptionReports reception, IncompleteCheckIns incomplete, StayAudit audit) {
    this.stays = stays;
    this.rooms = rooms;
    this.folios = folios;
    this.addOnCatalog = addOnCatalog;
    this.checkInOps = checkInOps;
    this.notices = notices;
    this.reception = reception;
    this.incomplete = incomplete;
    this.audit = audit;
  }

  /**
   * Checks the stay in: assigns the room (a null {@code roomNumber} keeps the reservation's), adds the
   * add-ons, moves the stay in house, occupies the room and opens the folio with the accommodation and
   * the add-ons' charges. The ancillaries selection is closed with it. A stay no longer arriving only
   * gets its selection closed.
   *
   * <p>Refused ({@link GuestNotices.NotAcknowledged}) while a guest has a blocking reception notice
   * the desk has not said it read; and ({@link IncompleteCheckIns.CheckInIncomplete}) while a step is
   * missing — a pax's document, the registration's signature —: those are done first, or the check-in
   * is forced ({@link #forceCheckIn}).
   */
  @Transactional
  public Stay checkIn(String stayId, String roomNumber, Collection<String> addOnIds) {
    return checkIn(stayId, roomNumber, addOnIds, null);
  }

  /** As {@link #checkIn(String, String, Collection)}, saying who asks — audited, done or refused. */
  @Transactional
  public Stay checkIn(String stayId, String roomNumber, Collection<String> addOnIds, String by) {
    return audit.run("Check-in", stayId, by, StayAudit.params("room", roomNumber, "addOns", addOnIds), () -> {
      var stay = find(stayId);
      if (stay.status() == StayStatus.ARRIVING) {
        notices.requireCheckIn(stay, by);
        incomplete.requireCompleteCheckIn(stay, by);
        stay = checkInNow(stay, roomNumber, addOnIds, by);
      }
      update(stayId, ops -> ops.withExtras(true));
      return stay;
    }, CheckInService::checkedInOutcome);
  }

  static String checkedInOutcome(Stay stay) {
    return stay.status() == StayStatus.IN_HOUSE ? "En casa · habitación " + stay.roomNumber() : "Estancia " + stay.status();
  }

  /**
   * The check-in forced with steps missing — the pax's documents, the registration's signature —, which
   * any receptionist can do with a {@code reason}: the guest is let in as with any check-in (up to the
   * PMS too), and the stay is left «Check-in incompleto» — audited, who, when, why and what was missing
   * — until the steps are done; it cannot check out meanwhile. A blocking reception notice must still be
   * read. With nothing missing it is a plain check-in.
   */
  @Transactional
  public Stay forceCheckIn(String stayId, String roomNumber, Collection<String> addOnIds, String reason, String by) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Forzar el check-in necesita un motivo");
    }
    return audit.run("Check-in", stayId, by,
        StayAudit.params("room", roomNumber, "addOns", addOnIds, "forced", true, "reason", reason), () -> {
          var stay = find(stayId);
          if (stay.status() == StayStatus.ARRIVING) {
            notices.requireCheckIn(stay, by);
            var missing = incomplete.missing(stay);
            stay = checkInNow(stay, roomNumber, addOnIds, by);
            if (!missing.isEmpty()) {
              incomplete.recordForced(stay, reason, missing, by);
            }
          }
          update(stayId, ops -> ops.withExtras(true));
          return stay;
        }, CheckInService::checkedInOutcome);
  }

  /** Checks the arriving stay in: room, add-ons, in house, the room occupied, the folio opened; up to the PMS. */
  Stay checkInNow(Stay stay, String roomNumber, Collection<String> addOnIds, String by) {
    var selected = roomNumber != null ? roomNumber : stay.roomNumber();
    var room = rooms.findByNumber(selected);
    stay = stay.assignRoom(selected, room.map(Room::typeLabel).orElse(stay.roomType()));
    for (var addOnId : addOnIds) {
      stay = stay.addAddOn(addOnId);
    }
    stay = stays.save(stay.completeCheckIn());
    room.filter(Room::assignable).ifPresent(r -> rooms.save(r.occupy()));
    Folio opened = null;
    if (folios.findByStayId(stay.id()).isEmpty()) {
      var contracted = stay.addOns().stream().map(SelectedAddOn::addOnId)
          .map(id -> addOnCatalog.findById(id).orElse(null)).filter(Objects::nonNull).toList();
      opened = folios.save(Folio.openAtCheckIn(stay, contracted));
    }
    // The PMS is the master of the stay: the check-in goes up to it, with this transaction — and the
    // extras the folio opened with after it, onto the PMS's folio (the accommodation is the PMS's own).
    reception.checkedIn(stay, by);
    if (opened != null) {
      var id = stay.id();
      opened.toThePms().forEach(line -> reception.chargePosted(id, line, by));
    }
    return stay;
  }

  @Transactional
  public CheckInOps wifiCreated(String stayId) {
    return audit.run("Wifi created", stayId, null, null, () -> update(stayId, ops -> ops.withWifi(true)), null);
  }

  @Transactional
  public CheckInOps keyEncoded(String stayId) {
    return audit.run("Key encoded", stayId, null, null, () -> update(stayId, ops -> ops.withLlave(true)), null);
  }

  @Transactional
  public CheckInOps registrationSigned(String stayId) {
    return registrationSigned(stayId, null);
  }

  /** The registration signed — which may be the last step a forced check-in owed. */
  @Transactional
  public CheckInOps registrationSigned(String stayId, String by) {
    return audit.run("Registration signed", stayId, by, null, () -> {
      var ops = update(stayId, o -> o.withFirma(true));
      incomplete.settle(stayId, by);
      return ops;
    }, null);
  }

  @Transactional
  public CheckInOps paymentTaken(String stayId) {
    return paymentTaken(stayId, null, null);
  }

  /**
   * The payment or pre-authorisation taken: {@code method} is how (card, cash, points) and {@code amount}
   * how much, as the desk said — for the audit trail; no card data is ever passed here.
   */
  @Transactional
  public CheckInOps paymentTaken(String stayId, String method, java.math.BigDecimal amount) {
    return audit.run("Payment taken", stayId, null, StayAudit.params("method", method, "amount", amount),
        () -> update(stayId, ops -> ops.withCobro(true)), null);
  }

  /** The ancillaries selection closed as it is. */
  @Transactional
  public CheckInOps extrasClosed(String stayId) {
    return audit.run("Ancillaries closed", stayId, null, null, () -> update(stayId, ops -> ops.withExtras(true)), null);
  }

  /** One add-on contracted or given up, the selection still open. */
  @Transactional
  public Stay addOnToggled(String stayId, String addOnId, boolean added) {
    return audit.run(added ? "Add-on added" : "Add-on removed", stayId, null, StayAudit.params("addOn", addOnId), () -> {
      var stay = find(stayId);
      return stays.save(added ? stay.addAddOn(addOnId) : stay.removeAddOn(addOnId));
    }, null);
  }

  /**
   * The ancillaries chosen, and the selection closed — together. {@code chosen} says, per add-on, if
   * the stay has it; an add-on it does not mention stays as it was.
   */
  @Transactional
  public Stay extrasChosen(String stayId, Map<String, Boolean> chosen) {
    return audit.run("Ancillaries chosen", stayId, null, StayAudit.params("chosen", chosen), () -> choose(stayId, chosen),
        null);
  }

  Stay choose(String stayId, Map<String, Boolean> chosen) {
    var stay = find(stayId);
    for (var entry : chosen.entrySet()) {
      var has = stay.addOns().stream().anyMatch(a -> a.addOnId().equals(entry.getKey()));
      if (entry.getValue() && !has) {
        stay = stay.addAddOn(entry.getKey());
      } else if (!entry.getValue() && has) {
        stay = stay.removeAddOn(entry.getKey());
      }
    }
    stay = stays.save(stay);
    update(stayId, ops -> ops.withExtras(true));
    return stay;
  }

  CheckInOps update(String stayId, UnaryOperator<CheckInOps> change) {
    return checkInOps.save(stayId, change.apply(checkInOps.of(stayId)));
  }

  Stay find(String stayId) {
    return stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
  }
}
