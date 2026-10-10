package io.mateu.ecdemo1.frontoffice.domain.stay;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The read side of stays, for screens that only look: a row per stay with its guest, and the day's
 * counters. Plain queries, not aggregates — loading a {@link Stay} brings its companions, incidents
 * and add-ons one query per stay, which a listing or a KPI never shows.
 */
public interface StayReadModel {

  /**
   * A stay as a listing shows it: its own columns, its guest's name and tier, and the holder's
   * nationality (what the desk scanned for pax 1, else the customer's) — null when nobody has said.
   */
  record StayRow(
      String id,
      StayStatus status,
      LocalDate checkIn,
      LocalDate checkOut,
      String roomNumber,
      String roomType,
      String guestName,
      String guestTier,
      String guestNationality) {

    public StayRow(String id, StayStatus status, LocalDate checkIn, LocalDate checkOut, String roomNumber,
                   String roomType, String guestName, String guestTier) {
      this(id, status, checkIn, checkOut, roomNumber, roomType, guestName, guestTier, null);
    }
  }

  /** The day at a glance: arrivals due (today or overdue), guests in house, departures today. */
  record Today(long arrivals, long inHouse, long departures) {}

  /**
   * What to look for among the stays; every criterion is optional (null: any) and they all apply.
   * {@code roomType}, {@code board}, {@code agency} and {@code text} match a part of the value,
   * ignoring case; {@code text} looks at the stay's id, the guest's name and the room number.
   * {@code nationality} (ISO-2) matches the holder's or — unless {@code holderOnly} — any pax's.
   * {@code occupyingOn} is a night the stay is in the hotel for: check-in on or before it, check-out
   * after it.
   */
  record StaySearch(
      Set<StayStatus> statuses,
      LocalDate arrivalFrom,
      LocalDate arrivalTo,
      LocalDate departureFrom,
      LocalDate departureTo,
      LocalDate occupyingOn,
      String roomType,
      String board,
      String agency,
      String nationality,
      boolean holderOnly,
      String text,
      int limit) {}

  /** The stays a search finds, earliest arrival first, at most {@code limit} — one query. */
  List<StayRow> search(StaySearch search);

  /** The holder's nationality of each of these stays that has one — one query. */
  Map<String, String> holderNationalities(Collection<String> stayIds);

  /** Every stay with its guest — one query. */
  List<StayRow> rows();

  /** The counters of {@code today} — one query. */
  Today today(LocalDate today);

  /** Rooms occupied each night from {@code from} for {@code days} nights — one query. */
  List<Long> occupiedNights(LocalDate from, int days);

  /** How many rooms the hotel has. */
  long rooms();
}
