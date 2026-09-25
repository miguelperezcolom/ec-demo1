package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;
import io.mateu.ecdemo1.booking.infra.in.ui.suppliers.CatalogLookup;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.ReadOnly;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A payment of the booking form. A row with no {@code paymentId} is a new one and is registered
 * when the form is saved; a registered payment cannot be changed.
 */
public record PaymentViewModel(
        @ReadOnly String paymentId,
        @NotNull PaymentType type,
        @Lookup(search = CatalogLookup.class, label = CatalogLookup.class) String methodCode,
        @NotNull BigDecimal amount,
        LocalDate date,
        String reference
) {
}
