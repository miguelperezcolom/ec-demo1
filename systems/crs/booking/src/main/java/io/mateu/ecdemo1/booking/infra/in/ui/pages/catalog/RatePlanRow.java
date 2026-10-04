package io.mateu.ecdemo1.booking.infra.in.ui.pages.catalog;

import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;

/** A rate plan a hotel sells, as a line of the listing: {@code id} is hotel/code. */
public record RatePlanRow(@Hidden String id, String hotel, String code, String name, String factor,
                          @Label("Codes") String codes) {
}
