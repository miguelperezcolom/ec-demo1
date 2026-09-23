package io.mateu.ecdemo1.frontoffice.domain.stay;

/**
 * Lifecycle of a stay: expected → checked in → checked out; or cancelled before arriving, when the
 * reservation it came from was cancelled in the CRS.
 */
public enum StayStatus {
  ARRIVING,
  IN_HOUSE,
  DEPARTED,
  CANCELLED
}
