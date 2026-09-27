package io.mateu.ecdemo1.frontoffice.domain.stay;

import java.time.LocalDate;
import java.util.List;

/**
 * The read side of stays, for screens that only look: a row per stay with its guest, and the day's
 * counters. Plain queries, not aggregates — loading a {@link Stay} brings its companions, incidents
 * and add-ons one query per stay, which a listing or a KPI never shows.
 */
public interface StayReadModel {

  /** A stay as a listing shows it: its own columns and its guest's name and tier. */
  record StayRow(
      String id,
      StayStatus status,
      LocalDate checkIn,
      LocalDate checkOut,
      String roomNumber,
      String roomType,
      String guestName,
      String guestTier) {}

  /** The day at a glance: arrivals due (today or overdue), guests in house, departures today. */
  record Today(long arrivals, long inHouse, long departures) {}

  /** Every stay with its guest — one query. */
  List<StayRow> rows();

  /** The counters of {@code today} — one query. */
  Today today(LocalDate today);

  /** Rooms occupied each night from {@code from} for {@code days} nights — one query. */
  List<Long> occupiedNights(LocalDate from, int days);

  /** How many rooms the hotel has. */
  long rooms();
}
