package io.mateu.ecdemo1.frontoffice.domain.stay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stay aggregate root — a booking's life at the hotel: room assignment, dates, board, the people
 * in the room, the incidents reported and the add-ons contracted. It owns the ARRIVING →
 * IN_HOUSE → DEPARTED lifecycle; the money owed lives in the Folio aggregate, the guest's cardex
 * in the Guest aggregate (referenced by {@code guestId}).
 */
public record Stay(
    String id,
    String guestId,
    String roomNumber,
    String roomType,
    String board,
    LocalDate checkIn,
    LocalDate checkOut,
    int pax,
    String agency,
    BigDecimal total,
    StayStatus status,
    int wishesGranted,
    int wishesTotal,
    String vipNote,
    List<Companion> companions,
    List<Incident> incidents,
    Set<SelectedAddOn> addOns) {

  public Stay {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Stay id is required");
    if (guestId == null || guestId.isBlank())
      throw new IllegalArgumentException("A stay belongs to a guest");
    if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn))
      throw new IllegalArgumentException("Check-out must be after check-in");
    if (pax < 1) throw new IllegalArgumentException("A stay hosts at least one person");
    companions = companions == null ? List.of() : List.copyOf(companions);
    incidents = incidents == null ? List.of() : List.copyOf(incidents);
    addOns = addOns == null ? Set.of() : Set.copyOf(addOns);
  }

  /** Where the stay sleeps, for a person: its room, or that it has none yet. */
  public String roomLabel() {
    return roomNumber == null || roomNumber.isBlank() ? "Sin asignar" : "Hab " + roomNumber;
  }

  /** Whether the stay has a room assigned — the room task of the check-in is done. */
  public boolean hasRoom() {
    return roomNumber != null && !roomNumber.isBlank();
  }

  /** Whether the stay takes a room: expected or in the house — not gone, not cancelled. */
  public boolean occupies() {
    return status == StayStatus.ARRIVING || status == StayStatus.IN_HOUSE;
  }

  /**
   * A stay for a reservation the CRS made, arriving, with no room yet: the desk assigns it at
   * check-in. Nothing operational — incidents, add-ons — is known before the guest arrives.
   */
  public static Stay fromReservation(String id, String guestId, String roomType, String board, LocalDate checkIn,
      LocalDate checkOut, int pax, String agency, BigDecimal total, List<Companion> companions) {
    return new Stay(id, guestId, null, roomType, board, checkIn, checkOut, pax, agency, total,
        StayStatus.ARRIVING, 0, 0, null, companions, List.of(), Set.of());
  }

  /**
   * The reservation changed in the CRS: what it says — dates, room type, board, pax, agency, price —
   * replaces what the stay had; what happened at the hotel — the room assigned, the check-in, the
   * incidents, the add-ons, the people registered at the desk — is the hotel's, and stays.
   */
  public Stay applyReservation(String guestId, String roomType, String board, LocalDate checkIn,
      LocalDate checkOut, int pax, String agency, BigDecimal total, List<Companion> reservationCompanions) {
    var keptCompanions = status == StayStatus.ARRIVING && companions.stream().noneMatch(Companion::documentVerified)
        ? reservationCompanions : companions;
    return new Stay(id, guestId, roomNumber, roomType, board, checkIn, checkOut, pax, agency, total, status,
        wishesGranted, wishesTotal, vipNote, keptCompanions, incidents, addOns);
  }

  /** The reservation was cancelled: a stay still to arrive is cancelled; one already in the house is not. */
  /**
   * The guests did not arrive: the CRS cancelled the booking as a no-show, and it costs what it says —
   * a share of the original price. Once: told again, the same stay.
   */
  public Stay noShow(java.math.BigDecimal fee) {
    if (status == StayStatus.NO_SHOW) {
      return this;
    }
    if (status != StayStatus.ARRIVING && status != StayStatus.CANCELLED) {
      throw new IllegalStateException("A stay " + status + " cannot be a no-show");
    }
    return new Stay(
        id, guestId, null, roomType, board, checkIn, checkOut, pax, agency, fee == null ? total : fee,
        StayStatus.NO_SHOW, wishesGranted, wishesTotal, vipNote, companions, incidents, addOns);
  }

  public Stay cancel() {
    if (status == StayStatus.CANCELLED || status == StayStatus.NO_SHOW) {
      return this;
    }
    if (status != StayStatus.ARRIVING) {
      throw new IllegalStateException("A stay " + status + " cannot be cancelled");
    }
    return withRoomAndStatus(null, roomType, StayStatus.CANCELLED);
  }

  public long nights() {
    return ChronoUnit.DAYS.between(checkIn, checkOut);
  }

  public boolean inHouse() {
    return status == StayStatus.IN_HOUSE;
  }

  public long openIncidents() {
    return incidents.stream().filter(i -> i.status() != IncidentStatus.RESOLVED).count();
  }

  /** Assigns (or moves to) a room while the guest has not checked in yet. */
  public Stay assignRoom(String roomNumber, String roomType) {
    if (status == StayStatus.DEPARTED)
      throw new IllegalStateException("Cannot assign a room to a departed stay");
    return withRoomAndStatus(roomNumber, roomType, status);
  }

  /** Completes the check-in: the stay needs an assigned room to become IN_HOUSE. */
  public Stay completeCheckIn() {
    if (status != StayStatus.ARRIVING)
      throw new IllegalStateException("Only an arriving stay can check in");
    if (roomNumber == null || roomNumber.isBlank())
      throw new IllegalStateException("A room must be assigned before checking in");
    return withRoomAndStatus(roomNumber, roomType, StayStatus.IN_HOUSE);
  }

  /** Completes the check-out of an in-house stay. */
  public Stay completeCheckOut() {
    if (status != StayStatus.IN_HOUSE)
      throw new IllegalStateException("Only an in-house stay can check out");
    return withRoomAndStatus(roomNumber, roomType, StayStatus.DEPARTED);
  }

  public Stay addCompanion(Companion companion) {
    List<Companion> updated = new ArrayList<>(companions);
    updated.add(companion);
    return new Stay(
        id, guestId, roomNumber, roomType, board, checkIn, checkOut, pax, agency, total, status,
        wishesGranted, wishesTotal, vipNote, updated, incidents, addOns);
  }

  /** The companion occupying pax slot {@code paxNumber} (2-based; pax 1 is the main guest). */
  public Companion companionAt(int paxNumber) {
    int idx = paxNumber - 2;
    return idx >= 0 && idx < companions.size() ? companions.get(idx) : null;
  }

  /**
   * Registers (or updates) the companion of pax slot {@code paxNumber} (2..pax), padding any
   * earlier empty slots with pending companions so slot order always matches pax order.
   */
  public Stay registerCompanion(int paxNumber, Companion companion) {
    if (paxNumber < 2 || paxNumber > pax)
      throw new IllegalArgumentException("Pax number out of the reservation's range");
    List<Companion> updated = new ArrayList<>(companions);
    while (updated.size() < paxNumber - 1) {
      updated.add(Companion.pending(updated.size() + 2));
    }
    updated.set(paxNumber - 2, companion);
    return new Stay(
        id, guestId, roomNumber, roomType, board, checkIn, checkOut, pax, agency, total, status,
        wishesGranted, wishesTotal, vipNote, updated, incidents, addOns);
  }

  /**
   * The desk's demo scanner read the document of companion {@code paxNumber}: the identity is seen —
   * with a made-up {@code ESC-…} document if none was known — and a contact filled where there was none.
   */
  public Stay scanCompanion(int paxNumber) {
    var companion = companionAt(paxNumber);
    if (companion == null) {
      companion = Companion.pending(paxNumber);
    }
    if (!companion.identityComplete()) {
      var document = companion.document() == null || companion.document().isBlank()
          ? "ESC-" + id.toUpperCase() + "-P" + paxNumber
          : companion.document();
      companion = companion.verifyIdentity(document);
    }
    if (companion.email() == null || companion.email().isBlank()) {
      companion = companion.updateContact(
          companion.name().toLowerCase().replace(' ', '.').replace("é", "e") + "@email.com",
          companion.phone() == null || companion.phone().isBlank() ? "+00 000 000 000" : companion.phone());
    }
    return registerCompanion(paxNumber, companion);
  }

  /**
   * Companion {@code paxNumber} registered by hand at the desk: document, name and contact in one go.
   * Without a document the identity is still marked as seen, with a made-up {@code MAN-…} one.
   */
  public Stay registerCompanionAtDesk(int paxNumber, String document, String name, String email, String phone) {
    var companion = companionAt(paxNumber);
    if (companion == null) {
      companion = Companion.pending(paxNumber);
    }
    var doc = document == null || document.isBlank() ? "MAN-" + id.toUpperCase() + "-P" + paxNumber : document;
    return registerCompanion(paxNumber, companion.rename(name).verifyIdentity(doc).updateContact(email, phone));
  }

  /** The contact of a companion already registered; a slot nobody registered stays as it is. */
  public Stay updateCompanionContact(int paxNumber, String email, String phone) {
    var companion = companionAt(paxNumber);
    return companion == null ? this : registerCompanion(paxNumber, companion.updateContact(email, phone));
  }

  public Stay reportIncident(Incident incident) {
    List<Incident> updated = new ArrayList<>(incidents);
    updated.add(incident);
    return new Stay(
        id, guestId, roomNumber, roomType, board, checkIn, checkOut, pax, agency, total, status,
        wishesGranted, wishesTotal, vipNote, companions, updated, addOns);
  }

  public Stay resolveIncident(String code) {
    List<Incident> updated =
        incidents.stream().map(i -> i.code().equals(code) ? i.resolve() : i).toList();
    return new Stay(
        id, guestId, roomNumber, roomType, board, checkIn, checkOut, pax, agency, total, status,
        wishesGranted, wishesTotal, vipNote, companions, updated, addOns);
  }

  public Stay addAddOn(String addOnId) {
    Set<SelectedAddOn> updated = new HashSet<>(addOns);
    updated.add(new SelectedAddOn(addOnId));
    return withAddOns(updated);
  }

  public Stay removeAddOn(String addOnId) {
    Set<SelectedAddOn> updated = new HashSet<>(addOns);
    updated.removeIf(a -> a.addOnId().equals(addOnId));
    return withAddOns(updated);
  }

  private Stay withRoomAndStatus(String roomNumber, String roomType, StayStatus status) {
    return new Stay(
        id, guestId, roomNumber, roomType, board, checkIn, checkOut, pax, agency, total, status,
        wishesGranted, wishesTotal, vipNote, companions, incidents, addOns);
  }

  private Stay withAddOns(Set<SelectedAddOn> addOns) {
    return new Stay(
        id, guestId, roomNumber, roomType, board, checkIn, checkOut, pax, agency, total, status,
        wishesGranted, wishesTotal, vipNote, companions, incidents, addOns);
  }
}
