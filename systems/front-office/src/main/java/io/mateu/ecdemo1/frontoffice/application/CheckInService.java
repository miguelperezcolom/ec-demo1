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
 */
@Service
public class CheckInService {

  final StayRepository stays;
  final RoomRepository rooms;
  final FolioRepository folios;
  final AddOnCatalogRepository addOnCatalog;
  final CheckInOpsRepository checkInOps;
  final GuestNotices notices;

  public CheckInService(StayRepository stays, RoomRepository rooms, FolioRepository folios,
                        AddOnCatalogRepository addOnCatalog, CheckInOpsRepository checkInOps, GuestNotices notices) {
    this.stays = stays;
    this.rooms = rooms;
    this.folios = folios;
    this.addOnCatalog = addOnCatalog;
    this.checkInOps = checkInOps;
    this.notices = notices;
  }

  /**
   * Checks the stay in: assigns the room (a null {@code roomNumber} keeps the reservation's), adds the
   * add-ons, moves the stay in house, occupies the room and opens the folio with the accommodation and
   * the add-ons' charges. The ancillaries selection is closed with it. A stay no longer arriving only
   * gets its selection closed.
   *
   * <p>Refused ({@link GuestNotices.NotAcknowledged}) while a guest has a blocking reception notice
   * the desk has not said it read.
   */
  @Transactional
  public Stay checkIn(String stayId, String roomNumber, Collection<String> addOnIds) {
    return checkIn(stayId, roomNumber, addOnIds, null);
  }

  /** As {@link #checkIn(String, String, Collection)}, saying who asks (for the audit of a refusal). */
  @Transactional
  public Stay checkIn(String stayId, String roomNumber, Collection<String> addOnIds, String by) {
    var stay = find(stayId);
    if (stay.status() == StayStatus.ARRIVING) {
      notices.requireCheckIn(stay, by);
      var selected = roomNumber != null ? roomNumber : stay.roomNumber();
      var room = rooms.findByNumber(selected);
      stay = stay.assignRoom(selected, room.map(Room::typeLabel).orElse(stay.roomType()));
      for (var addOnId : addOnIds) {
        stay = stay.addAddOn(addOnId);
      }
      stay = stays.save(stay.completeCheckIn());
      room.filter(Room::assignable).ifPresent(r -> rooms.save(r.occupy()));
      if (folios.findByStayId(stay.id()).isEmpty()) {
        var contracted = stay.addOns().stream().map(SelectedAddOn::addOnId)
            .map(id -> addOnCatalog.findById(id).orElse(null)).filter(Objects::nonNull).toList();
        folios.save(Folio.openAtCheckIn(stay, contracted));
      }
    }
    update(stayId, ops -> ops.withExtras(true));
    return stay;
  }

  @Transactional
  public CheckInOps wifiCreated(String stayId) {
    return update(stayId, ops -> ops.withWifi(true));
  }

  @Transactional
  public CheckInOps keyEncoded(String stayId) {
    return update(stayId, ops -> ops.withLlave(true));
  }

  @Transactional
  public CheckInOps registrationSigned(String stayId) {
    return update(stayId, ops -> ops.withFirma(true));
  }

  @Transactional
  public CheckInOps paymentTaken(String stayId) {
    return update(stayId, ops -> ops.withCobro(true));
  }

  /** The ancillaries selection closed as it is. */
  @Transactional
  public CheckInOps extrasClosed(String stayId) {
    return update(stayId, ops -> ops.withExtras(true));
  }

  /** One add-on contracted or given up, the selection still open. */
  @Transactional
  public Stay addOnToggled(String stayId, String addOnId, boolean added) {
    var stay = find(stayId);
    return stays.save(added ? stay.addAddOn(addOnId) : stay.removeAddOn(addOnId));
  }

  /**
   * The ancillaries chosen, and the selection closed — together. {@code chosen} says, per add-on, if
   * the stay has it; an add-on it does not mention stays as it was.
   */
  @Transactional
  public Stay extrasChosen(String stayId, Map<String, Boolean> chosen) {
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
