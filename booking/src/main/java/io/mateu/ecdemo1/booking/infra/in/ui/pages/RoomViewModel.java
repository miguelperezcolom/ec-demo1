package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.infra.in.ui.suppliers.CatalogLookup;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.ReadOnly;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

/**
 * A room of the booking form. {@code line} and {@code total} are the CRS's — the room's number in
 * the booking and what it costs once priced — and are shown, not entered. The list shows what tells
 * the rooms apart; the rest is in the row's form.
 */
public record RoomViewModel(
        @ReadOnly Integer line,
        @NotEmpty @Lookup(search = CatalogLookup.class, label = CatalogLookup.class) String roomTypeCode,
        @HiddenInList @NotEmpty @Lookup(search = CatalogLookup.class, label = CatalogLookup.class) String ratePlanCode,
        @HiddenInList @NotEmpty @Lookup(search = CatalogLookup.class, label = CatalogLookup.class) String boardCode,
        @Min(1) int adults,
        @HiddenInList List<Integer> childrenAges,
        @ReadOnly BigDecimal total
) {
}
