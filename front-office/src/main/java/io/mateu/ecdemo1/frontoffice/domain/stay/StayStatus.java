package io.mateu.ecdemo1.frontoffice.domain.stay;

/** Lifecycle of a stay: expected today → checked in → checked out. */
public enum StayStatus {
  ARRIVING,
  IN_HOUSE,
  DEPARTED
}
