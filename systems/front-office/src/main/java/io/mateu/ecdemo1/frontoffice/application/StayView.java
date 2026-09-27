package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;

/** A stay with its guest and its folio (none before the check-in) — what every front-office screen works on. */
public record StayView(Stay stay, Guest guest, Folio folio) {}
