package io.mateu.ecdemo1.frontoffice.domain.stay;

/**
 * Lifecycle of a stay: expected → checked in → checked out; or cancelled before arriving, when the
 * reservation it came from was cancelled in the CRS.
 */
public enum StayStatus {
  ARRIVING,
  IN_HOUSE,
  DEPARTED,
  CANCELLED,
  /** The guests did not arrive: cancelled in the CRS as a no-show, and it still costs its fee. */
  NO_SHOW
}
