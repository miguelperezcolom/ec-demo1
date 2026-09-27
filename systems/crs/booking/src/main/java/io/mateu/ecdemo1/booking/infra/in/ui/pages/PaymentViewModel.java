package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;
import io.mateu.ecdemo1.booking.infra.in.ui.suppliers.CatalogLookup;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.ReadOnly;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A payment of the booking form. A row with no {@code paymentId} is a new one and is registered
 * when the form is saved; a registered payment cannot be changed. The list shows what and how much;
 * the method and the reference are in the row's form.
 */
public record PaymentViewModel(
        @HiddenInList @ReadOnly String paymentId,
        @NotNull PaymentType type,
        @HiddenInList @Lookup(search = CatalogLookup.class, label = CatalogLookup.class) String methodCode,
        @NotNull BigDecimal amount,
        LocalDate date,
        @HiddenInList String reference
) {
}
