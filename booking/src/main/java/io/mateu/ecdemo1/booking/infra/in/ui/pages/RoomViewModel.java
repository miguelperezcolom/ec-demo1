package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.infra.in.ui.suppliers.CatalogLookup;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.ReadOnly;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

/**
 * A room of the booking form. {@code line} and {@code total} are the CRS's — the room's number in
 * the booking and what it costs once priced — and are shown, not entered.
 */
public record RoomViewModel(
        @ReadOnly Integer line,
        @NotEmpty @Lookup(search = CatalogLookup.class, label = CatalogLookup.class) String roomTypeCode,
        @NotEmpty @Lookup(search = CatalogLookup.class, label = CatalogLookup.class) String ratePlanCode,
        @NotEmpty @Lookup(search = CatalogLookup.class, label = CatalogLookup.class) String boardCode,
        @Min(1) int adults,
        List<Integer> childrenAges,
        @ReadOnly BigDecimal total
) {
}
