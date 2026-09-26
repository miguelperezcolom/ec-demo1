package io.mateu.ecdemo1.frontoffice.ui.common;

import io.mateu.ecdemo1.frontoffice.domain.guest.GuestTier;
import io.mateu.uidl.data.Chip;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;

/**
 * How a loyalty tier looks: a coloured badge rather than the constant, the same in the listing and
 * in the guest's banner. Mateu's badges have semantic tones only, so each tier borrows the one that
 * reads like its metal — gold amber, platinum blue, silver grey.
 */
public final class Tiers {

  private Tiers() {}

  public static String label(GuestTier tier) {
    return switch (tier) {
      case PLATINUM -> "Platinum";
      case GOLD -> "Gold";
      case SILVER -> "Silver";
    };
  }

  /** The listing's cell: a badge. An unknown or missing tier is shown as it came, uncoloured. */
  public static Status badge(String tier) {
    if (tier == null || tier.isBlank()) {
      return null;
    }
    GuestTier known;
    try {
      known = GuestTier.valueOf(tier);
    } catch (IllegalArgumentException e) {
      return new Status(StatusType.NONE, tier);
    }
    var type = switch (known) {
      case PLATINUM -> StatusType.INFO;
      case GOLD -> StatusType.WARNING;
      case SILVER -> StatusType.NONE;
    };
    return new Status(type, label(known), tier);
  }

  /** The banner's chip, in the same tone as the listing's badge. */
  public static Chip chip(GuestTier tier) {
    var color = switch (tier) {
      case PLATINUM -> "normal";
      case GOLD -> "warning";
      case SILVER -> "contrast";
    };
    return Chip.builder().label(label(tier)).color(color).build();
  }
}
